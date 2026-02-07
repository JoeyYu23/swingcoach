idf_component_register(SRCS "main.cpp"
                       PRIV_REQUIRES spi_flash nvs_flash esp_wifi esp_netif esp_http_client esp_event
                       INCLUDE_DIRS "")
