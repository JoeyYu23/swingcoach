/*
 * ESP32-S3 BNO085 IMU — Dual-Mode Streaming (HTTP + UDP)
 * Live 200Hz stream + event capture at 400Hz on swing detection
 * Event: HTTP POST JSON, Live: UDP JSON
 */

#include <stdio.h>
#include <string.h>
#include <math.h>
#include <sys/time.h>
#include <time.h>
#include <stdarg.h>
#include <stdlib.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/event_groups.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_heap_caps.h"
#include "esp_timer.h"
#include "esp_system.h"
#include "nvs_flash.h"
#include "esp_netif_sntp.h"
#include "esp_http_client.h"
#include "lwip/sockets.h"
#include "lwip/inet.h"
#include "BNO08x.hpp"

// ===== Configuration (edit these) =====
#define WIFI_SSID          "Columbia University"
#define WIFI_PASSWORD      ""
#define SERVER_IP          "10.206.81.71"
// #define SERVER_IP          "10.206.99.24"
#define SERVER_PORT        7103
#define SERVER_URL         "http://" SERVER_IP ":7103/"
#define LIVE_UDP_PORT      7104
// =======================================

// Sensor timing
#define SENSOR_PERIOD_US        2500UL      // 400Hz for all reports
#define SPI_CLOCK_HZ            2000000     // 2MHz SPI

// Live streaming
#define LIVE_DECIMATION         2           // 400Hz / 2 = 200Hz live output
#define LIVE_POST_INTERVAL_MS   50          // send live batch every 50ms
#define MAX_LIVE_PER_POST       50          // max samples in one live send

// Event detection
#define ACCEL_THRESHOLD_MS2     30.0f       // ~3g, swing acceleration threshold
#define EVENT_DEBOUNCE_MS       1000        // ignore triggers for 1s after event
#define EVENT_PRE_SAMPLES       80          // 200ms * 400Hz
#define EVENT_POST_SAMPLES      120         // 300ms * 400Hz

// Ring buffer
#define RING_BUF_SIZE           200         // pre + post = 200 samples

// Live queue
#define LIVE_QUEUE_SIZE         100

static const char *TAG = "racquet";

// --- Data structures ---

typedef struct __attribute__((packed)) {
    float euler_x, euler_y, euler_z;
    float gyro_x, gyro_y, gyro_z;
    float accel_x, accel_y, accel_z;
    int64_t timestamp_ms;
    uint32_t seq;
} sensor_sample_t;  // 48 bytes

typedef enum {
    STATE_NORMAL,
    STATE_CAPTURING
} stream_state_t;

typedef struct {
    int64_t trigger_timestamp_ms;
    float trigger_gyro_mag;
    uint32_t post_samples_needed;
    uint32_t post_samples_count;
} event_context_t;

// --- Globals ---

// Ring buffer (400Hz, all samples)
static sensor_sample_t ring_buffer[RING_BUF_SIZE];
static uint32_t ring_head = 0;
static uint32_t ring_count = 0;

// Event snapshot (copied from ring buffer when capture completes)
static sensor_sample_t event_snapshot[RING_BUF_SIZE];
static volatile int event_snapshot_count = 0;
static volatile int64_t event_snapshot_trigger_t = 0;
static volatile bool event_ready = false;

// State
static stream_state_t current_state = STATE_NORMAL;
static event_context_t evt_ctx = {};

// Queues and sync
static QueueHandle_t live_queue = NULL;
static EventGroupHandle_t wifi_event_group = NULL;
#define WIFI_CONNECTED_BIT BIT0

// HTTP client
static esp_http_client_handle_t http_client = NULL;
static int udp_sock = -1;
static struct sockaddr_in udp_dest_addr = {};

// Mutex for event snapshot shared between cores
static SemaphoreHandle_t event_mutex = NULL;

// --- Ring buffer helpers ---

static inline void ring_write(const sensor_sample_t *s)
{
    ring_buffer[ring_head % RING_BUF_SIZE] = *s;
    ring_head++;
    if (ring_count < RING_BUF_SIZE) ring_count++;
}

static int ring_copy_recent(sensor_sample_t *dest, int count)
{
    if ((uint32_t)count > ring_count) count = (int)ring_count;
    for (int i = 0; i < count; i++) {
        uint32_t idx = (ring_head - count + i) % RING_BUF_SIZE;
        dest[i] = ring_buffer[idx];
    }
    return count;
}

// --- Event detection ---

static inline bool check_event_trigger(const sensor_sample_t *s, float *out_mag)
{
    float mag = sqrtf(s->accel_x * s->accel_x +
                      s->accel_y * s->accel_y +
                      s->accel_z * s->accel_z);
    *out_mag = mag;
    return (mag > ACCEL_THRESHOLD_MS2);
}

static void finalize_event_snapshot(void)
{
    int total = EVENT_PRE_SAMPLES + EVENT_POST_SAMPLES;
    if (xSemaphoreTake(event_mutex, 0) == pdTRUE) {
        event_snapshot_count = ring_copy_recent(event_snapshot, total);
        event_snapshot_trigger_t = evt_ctx.trigger_timestamp_ms;
        event_ready = true;
        xSemaphoreGive(event_mutex);
        printf("Event captured: %d samples, trigger=%.1f m/s2\n",
               event_snapshot_count, evt_ctx.trigger_gyro_mag);
    }
    current_state = STATE_NORMAL;
}

// --- Wi-Fi ---

static void wifi_event_handler(void *arg, esp_event_base_t event_base,
                               int32_t event_id, void *event_data)
{
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_CONNECTED) {
        printf("Wi-Fi: Connected to AP, waiting for IP...\n");
    } else if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        printf("Wi-Fi: Disconnected! Retrying...\n");
        xEventGroupClearBits(wifi_event_group, WIFI_CONNECTED_BIT);
        esp_wifi_connect();
    } else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)event_data;
        printf("*** ESP32 IP Address: " IPSTR " ***\n", IP2STR(&event->ip_info.ip));
        xEventGroupSetBits(wifi_event_group, WIFI_CONNECTED_BIT);
    }
}

static void wifi_init_sta(void)
{
    wifi_event_group = xEventGroupCreate();

    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));

    ESP_ERROR_CHECK(esp_event_handler_instance_register(
        WIFI_EVENT, ESP_EVENT_ANY_ID, &wifi_event_handler, NULL, NULL));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(
        IP_EVENT, IP_EVENT_STA_GOT_IP, &wifi_event_handler, NULL, NULL));

    wifi_config_t wifi_config = {};
    strncpy((char *)wifi_config.sta.ssid, WIFI_SSID, sizeof(wifi_config.sta.ssid));
    strncpy((char *)wifi_config.sta.password, WIFI_PASSWORD, sizeof(wifi_config.sta.password));
    wifi_config.sta.threshold.authmode = WIFI_AUTH_OPEN;

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_ERROR_CHECK(esp_wifi_start());

    printf("Wi-Fi: Connecting to \"%s\"...\n", WIFI_SSID);
    esp_wifi_connect();
}

// --- HTTP JSON helpers ---

static bool json_append(char **dst, size_t *remaining, const char *fmt, ...)
{
    if (*remaining == 0) return false;
    va_list args;
    va_start(args, fmt);
    int written = vsnprintf(*dst, *remaining, fmt, args);
    va_end(args);
    if (written < 0 || (size_t)written >= *remaining) return false;
    *dst += written;
    *remaining -= (size_t)written;
    return true;
}

static int build_json_payload(const char *type, const sensor_sample_t *samples,
                              int count, int64_t trigger_t,
                              char *out_buf, size_t out_size)
{
    char *p = out_buf;
    size_t rem = out_size;

    if (!json_append(&p, &rem, "{\"type\":\"%s\",\"samples\":[", type)) return -1;

    for (int i = 0; i < count; i++) {
        const sensor_sample_t *s = &samples[i];
        if (i > 0) {
            if (!json_append(&p, &rem, ",")) return -1;
        }
        if (!json_append(
                &p, &rem,
                "{\"t\":%lld,"
                "\"gyro\":{\"x\":%.3f,\"y\":%.3f,\"z\":%.3f},"
                "\"accel\":{\"x\":%.3f,\"y\":%.3f,\"z\":%.3f}}",
                (long long)s->timestamp_ms,
                s->gyro_x, s->gyro_y, s->gyro_z,
                s->accel_x, s->accel_y, s->accel_z)) {
            return -1;
        }
    }

    if (!json_append(&p, &rem, "]")) return -1;

    if (strcmp(type, "event") == 0) {
        if (!json_append(&p, &rem, ",\"trigger_t\":%lld", (long long)trigger_t)) return -1;
    }

    if (!json_append(&p, &rem, "}")) return -1;

    return (int)(p - out_buf);
}

static bool http_post_json(const char *json, int len)
{
    if (http_client == NULL) return false;

    esp_http_client_set_url(http_client, SERVER_URL);
    esp_http_client_set_method(http_client, HTTP_METHOD_POST);
    esp_http_client_set_header(http_client, "Content-Type", "application/json");
    esp_http_client_set_post_field(http_client, json, len);

    int64_t t0 = esp_timer_get_time();

    esp_err_t err = esp_http_client_perform(http_client);
    int64_t dur_ms = (esp_timer_get_time() - t0) / 1000;
    if (dur_ms > 500) {
        printf("HTTP: POST took %lld ms\n", (long long)dur_ms);
    }

    if (err != ESP_OK) {
        printf("HTTP: POST failed: %s (%lld ms) | heap=%u\n",
               esp_err_to_name(err), (long long)dur_ms,
               (unsigned)heap_caps_get_free_size(MALLOC_CAP_8BIT));
        // Tear down and recreate client to recover from stale connections
        esp_http_client_cleanup(http_client);
        esp_http_client_config_t config = {};
        config.url = SERVER_URL;
        config.timeout_ms = 2000;
        config.keep_alive_enable = false;
        http_client = esp_http_client_init(&config);
        return false;
    }

    int status = esp_http_client_get_status_code(http_client);
    esp_http_client_close(http_client);
    if (status != 200) {
        printf("HTTP: Non-200 status: %d\n", status);
        return false;
    }
    return true;
}

static bool udp_send_json(const char *json, int len)
{
    if (udp_sock < 0) return false;
    int sent = sendto(udp_sock, json, len, 0,
                      (struct sockaddr *)&udp_dest_addr, sizeof(udp_dest_addr));
    if (sent != len) {
        printf("UDP: sendto failed (sent=%d, len=%d)\n", sent, len);
        return false;
    }
    return true;
}

// --- HTTP event send task (Core 0) ---

static void http_event_task(void *pvParameters)
{
    ESP_LOGI(TAG, "HTTP event task waiting for Wi-Fi...");
    xEventGroupWaitBits(wifi_event_group, WIFI_CONNECTED_BIT,
                        pdFALSE, pdTRUE, portMAX_DELAY);

    // Sync real time via SNTP
    esp_sntp_config_t sntp_config = ESP_NETIF_SNTP_DEFAULT_CONFIG("pool.ntp.org");
    esp_netif_sntp_init(&sntp_config);
    printf("Syncing time via SNTP...\n");
    if (esp_netif_sntp_sync_wait(pdMS_TO_TICKS(15000)) == ESP_OK) {
        time_t now;
        struct tm timeinfo;
        time(&now);
        localtime_r(&now, &timeinfo);
        printf("Time synced: %04d-%02d-%02d %02d:%02d:%02d UTC\n",
               timeinfo.tm_year + 1900, timeinfo.tm_mon + 1, timeinfo.tm_mday,
               timeinfo.tm_hour, timeinfo.tm_min, timeinfo.tm_sec);
    } else {
        printf("SNTP sync failed, using uptime timestamps.\n");
    }

    // Init HTTP client
    esp_http_client_config_t config = {};
    config.url = SERVER_URL;
    config.timeout_ms = 2000;
    config.keep_alive_enable = false;
    http_client = esp_http_client_init(&config);

    printf("HTTP event task started.\n");

    while (1) {
        // Sleep until there's an event to send
        vTaskDelay(pdMS_TO_TICKS(100));

        EventBits_t bits = xEventGroupGetBits(wifi_event_group);
        if (!(bits & WIFI_CONNECTED_BIT)) continue;

        if (!event_ready) continue;

        // Take mutex to safely read the snapshot
        if (xSemaphoreTake(event_mutex, pdMS_TO_TICKS(50)) != pdTRUE) continue;

        size_t max_size = 65536;
        char *json = (char *)malloc(max_size);
        if (json == NULL) {
            xSemaphoreGive(event_mutex);
            printf("HTTP: OOM building event JSON\n");
            continue;
        }
        int snap_count = event_snapshot_count;
        int64_t snap_trigger = event_snapshot_trigger_t;
        int len = build_json_payload("event", event_snapshot, snap_count,
                                     snap_trigger, json, max_size);
        xSemaphoreGive(event_mutex);

        if (len <= 0) {
            printf("HTTP: Event JSON build failed (count=%d, heap=%u)\n",
                   snap_count, (unsigned)heap_caps_get_free_size(MALLOC_CAP_8BIT));
            free(json);
            continue;
        }

        bool ok = http_post_json(json, len);
        free(json);

        if (ok) {
            event_ready = false;
            printf("Event sent (%d samples)\n", snap_count);
        } else {
            printf("Event send failed, will retry.\n");
        }
    }
}

// --- UDP live send task (Core 0) ---

static void udp_live_task(void *pvParameters)
{
    sensor_sample_t batch[MAX_LIVE_PER_POST];

    ESP_LOGI(TAG, "UDP live task waiting for Wi-Fi...");
    xEventGroupWaitBits(wifi_event_group, WIFI_CONNECTED_BIT,
                        pdFALSE, pdTRUE, portMAX_DELAY);

    // Init UDP socket
    udp_sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (udp_sock < 0) {
        printf("UDP: socket() failed\n");
        vTaskDelete(NULL);
        return;
    }
    udp_dest_addr.sin_family = AF_INET;
    udp_dest_addr.sin_port = htons(LIVE_UDP_PORT);
    inet_aton(SERVER_IP, &udp_dest_addr.sin_addr);
    printf("UDP live stream to %s:%d\n", SERVER_IP, LIVE_UDP_PORT);

    while (1) {
        vTaskDelay(pdMS_TO_TICKS(LIVE_POST_INTERVAL_MS));

        EventBits_t bits = xEventGroupGetBits(wifi_event_group);
        if (!(bits & WIFI_CONNECTED_BIT)) continue;

        int count = 0;
        while (count < MAX_LIVE_PER_POST &&
               xQueueReceive(live_queue, &batch[count], 0) == pdTRUE) {
            count++;
        }

        if (count > 0) {
            size_t max_size = 16384;
            char *json = (char *)malloc(max_size);
            if (json == NULL) {
                printf("LIVE: OOM building live JSON\n");
                continue;
            }
            int len = build_json_payload("live", batch, count, 0, json, max_size);
            if (len <= 0) {
                printf("LIVE: JSON build failed (count=%d, heap=%u)\n",
                       count, (unsigned)heap_caps_get_free_size(MALLOC_CAP_8BIT));
            }
            bool ok = (len > 0) && udp_send_json(json, len);
            free(json);
            if (!ok) {
                printf("Live send failed\n");
            }
        }
    }
}

// --- Sensor task (Core 1) ---

static void sensor_task(void *pvParameters)
{
    BNO08x *imu = (BNO08x *)pvParameters;

    imu->rpt.rv_game.enable(SENSOR_PERIOD_US);
    imu->rpt.cal_gyro.enable(SENSOR_PERIOD_US);
    imu->rpt.accelerometer.enable(SENSOR_PERIOD_US);

    printf("Sensor task started (400Hz, live decimated to 200Hz)\n");

    sensor_sample_t current_sample = {};
    uint32_t sample_seq = 0;
    int64_t last_event_time_ms = 0;

    while (1) {
        if (!imu->data_available()) {
            vTaskDelay(1);  // yield to prevent TWDT starvation
            continue;
        }

        bool got_data = false;

        if (imu->rpt.rv_game.has_new_data()) {
            bno08x_euler_angle_t euler = imu->rpt.rv_game.get_euler();
            current_sample.euler_x = euler.x;
            current_sample.euler_y = euler.y;
            current_sample.euler_z = euler.z;
            got_data = true;
        }

        if (imu->rpt.cal_gyro.has_new_data()) {
            bno08x_gyro_t gyro = imu->rpt.cal_gyro.get();
            current_sample.gyro_x = gyro.x;
            current_sample.gyro_y = gyro.y;
            current_sample.gyro_z = gyro.z;
            got_data = true;
        }

        if (imu->rpt.accelerometer.has_new_data()) {
            bno08x_accel_t accel = imu->rpt.accelerometer.get();
            current_sample.accel_x = accel.x;
            current_sample.accel_y = accel.y;
            current_sample.accel_z = accel.z;
            got_data = true;
        }

        if (!got_data) continue;

        // Timestamp and sequence
        struct timeval tv;
        gettimeofday(&tv, NULL);
        current_sample.timestamp_ms = (int64_t)tv.tv_sec * 1000 + tv.tv_usec / 1000;
        current_sample.seq = sample_seq++;

        // Always write to ring buffer (400Hz)
        ring_write(&current_sample);

        // Live stream: decimate to 200Hz
        if (current_sample.seq % LIVE_DECIMATION == 0) {
            xQueueSend(live_queue, &current_sample, 0);
        }

        // State machine
        switch (current_state) {
            case STATE_NORMAL: {
                float gyro_mag = 0.0f;
                int64_t now = current_sample.timestamp_ms;
                bool debounce_ok = (now - last_event_time_ms) > EVENT_DEBOUNCE_MS;

                if (debounce_ok && !event_ready && check_event_trigger(&current_sample, &gyro_mag)) {
                    current_state = STATE_CAPTURING;
                    evt_ctx.trigger_timestamp_ms = now;
                    evt_ctx.trigger_gyro_mag = gyro_mag;
                    evt_ctx.post_samples_needed = EVENT_POST_SAMPLES;
                    evt_ctx.post_samples_count = 0;
                    last_event_time_ms = now;
                    printf("EVENT TRIGGERED! accel=%.1f m/s2\n", gyro_mag);
                }
                break;
            }

            case STATE_CAPTURING: {
                evt_ctx.post_samples_count++;
                if (evt_ctx.post_samples_count >= evt_ctx.post_samples_needed) {
                    finalize_event_snapshot();
                }
                break;
            }
        }
    }
}

// --- Main ---

extern "C" void app_main(void)
{
    printf("BNO085 Dual-Mode IMU — ESP32-S3 (HTTP+UDP)\n");

    // Init NVS (required for Wi-Fi)
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    // Init Wi-Fi and wait for connection
    wifi_init_sta();
    printf("Waiting for Wi-Fi connection...\n");
    xEventGroupWaitBits(wifi_event_group, WIFI_CONNECTED_BIT,
                        pdFALSE, pdTRUE, portMAX_DELAY);
    printf("Wi-Fi connected!\n");

    // Create live queue and event mutex
    live_queue = xQueueCreate(LIVE_QUEUE_SIZE, sizeof(sensor_sample_t));
    if (live_queue == NULL) {
        ESP_LOGE(TAG, "Failed to create live queue!");
        return;
    }
    event_mutex = xSemaphoreCreateMutex();
    if (event_mutex == NULL) {
        ESP_LOGE(TAG, "Failed to create event mutex!");
        return;
    }

    // Init BNO085
    bno08x_config_t imu_config(
        SPI2_HOST,
        GPIO_NUM_11,    // MOSI
        GPIO_NUM_13,    // MISO
        GPIO_NUM_12,    // SCLK
        GPIO_NUM_10,    // CS
        GPIO_NUM_14,    // INT
        GPIO_NUM_15,    // RST
        SPI_CLOCK_HZ
    );

    static BNO08x imu(imu_config);

    printf("Initializing BNO085...\n");
    if (!imu.initialize()) {
        printf("ERROR: Failed to initialize BNO085!\n");
        return;
    }
    printf("BNO085 initialized.\n");

    // Launch tasks: HTTP event + UDP live on Core 0, sensor on Core 1
    xTaskCreatePinnedToCore(http_event_task, "http_event", 8192, NULL, 4, NULL, 0);
    xTaskCreatePinnedToCore(udp_live_task, "udp_live", 6144, NULL, 5, NULL, 0);
    xTaskCreatePinnedToCore(sensor_task, "sensor", 8192, (void *)&imu, 8, NULL, 1);
}
