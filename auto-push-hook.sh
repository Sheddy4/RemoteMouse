#!/bin/sh
#
# Git post-commit hook — автоматически пушит после каждого коммита.
# 
# Установка:
#   1. Скопируй этот файл в .git/hooks/post-commit
#   2. Сделай его исполняемым: chmod +x .git/hooks/post-commit
#   3. Настрой remote: git remote add origin https://github.com/YOUR_USERNAME/RemoteMouse.git
#
# Теперь после каждого `git commit` изменения автоматически пушатся на GitHub.

BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "Auto-pushing to origin/$BRANCH..."
git push origin "$BRANCH" 2>&1

if [ $? -eq 0 ]; then
    echo "✅ Pushed successfully!"
else
    echo "⚠️ Push failed. Check your remote and credentials."
fi
