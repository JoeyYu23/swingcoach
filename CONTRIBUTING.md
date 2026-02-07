# Contributing to SwingCoach

Thank you for your interest in contributing to SwingCoach!

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in Issues
2. Create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment details (OS, Python version, etc.)

### Suggesting Features

1. Open an issue with the "enhancement" label
2. Describe the feature and its use case
3. Discuss implementation approach

### Pull Requests

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Write/update tests
5. Ensure tests pass: `pytest`
6. Commit with clear messages
7. Push and create a Pull Request

## Code Style

### Python
- Follow PEP 8
- Use type hints
- Document functions with docstrings

### TypeScript/React
- Use functional components with hooks
- Follow ESLint rules
- Use TypeScript strict mode

## Development Setup

```bash
# Clone
git clone https://github.com/YOUR_USERNAME/swingcoach.git
cd swingcoach

# Backend
cd backend
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
pip install pytest black flake8

# Frontend
cd ../frontend
npm install
```

## Testing

```bash
# Backend
cd backend
pytest

# Frontend
cd frontend
npm test
```

## Questions?

Open an issue or reach out to the maintainers.
