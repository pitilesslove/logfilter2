#!/bin/bash
# K8s Log Viewer 서버 실행 스크립트 (tmux)

SESSION_NAME="logfilter"
PROJECT_DIR="/Users/mhchoi/projects/logfilter"
SCRIPT_DIR="/tmp/logfilter-scripts-$$"

mkdir -p "$SCRIPT_DIR"

# logfilter 서버 스크립트
cat > "$SCRIPT_DIR/server.sh" << EOF
#!/bin/bash
echo '# [K8s Log Viewer]'
echo '# Access: http://localhost:8888'
echo
cd $PROJECT_DIR
exec npm start
EOF

chmod +x "$SCRIPT_DIR"/*.sh

# tmux 세션 시작
tmux kill-session -t "$SESSION_NAME" 2>/dev/null
tmux new-session -d -s "$SESSION_NAME" -n "server"

tmux send-keys -t "$SESSION_NAME:server" "$SCRIPT_DIR/server.sh" Enter

echo "✅ K8s Log Viewer started in tmux session: '$SESSION_NAME'"
echo "👉 Attach with: tmux attach -t $SESSION_NAME"
