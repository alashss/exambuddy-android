#!/bin/bash
# ============================================================
# ExamBuddy — Termux Setup & Push ke GitHub
# Jalankan dari folder project: bash TERMUX_SETUP.sh
# (Asumsi: gh CLI sudah login / git sudah connect GitHub)
# ============================================================

set -e

PROJECT_NAME="ExamBuddy"
REPO_NAME="exambuddy-android"
GITHUB_USER=$(gh api user --jq .login 2>/dev/null || git config user.name)

echo "==================================================="
echo "  ExamBuddy — Setup GitHub Repo"
echo "  User: $GITHUB_USER"
echo "  Repo: $REPO_NAME"
echo "==================================================="

# 1. Init git
git init
git add .
git commit -m "Initial commit — ExamBuddy AI exam overlay"

# 2. Buat repo baru di GitHub (public)
echo ""
echo "Membuat repo GitHub..."
gh repo create "$REPO_NAME" \
  --public \
  --description "🤖 AI floating bubble untuk bantu jawab soal ujian — multi provider (Gemini, Groq, OpenRouter)" \
  --source=. \
  --remote=origin \
  --push

echo ""
echo "✅ Repo dibuat: https://github.com/$GITHUB_USER/$REPO_NAME"
echo ""
echo "GitHub Actions akan auto-build dan release APK!"
echo "Cek di: https://github.com/$GITHUB_USER/$REPO_NAME/actions"
echo ""
echo "APK akan tersedia di:"
echo "https://github.com/$GITHUB_USER/$REPO_NAME/releases"
