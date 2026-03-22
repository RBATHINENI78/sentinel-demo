#!/usr/bin/env bash
set -euo pipefail

APP_NAME="sentinel-demo"
PORT=8080
PID_FILE=".app.pid"

stop_if_running() {
    # Check by PID file first
    if [ -f "$PID_FILE" ]; then
        OLD_PID=$(cat "$PID_FILE")
        if kill -0 "$OLD_PID" 2>/dev/null; then
            echo "[$APP_NAME] Stopping running instance (PID $OLD_PID)..."
            kill "$OLD_PID"
            # Wait for it to exit
            for i in $(seq 1 15); do
                if ! kill -0 "$OLD_PID" 2>/dev/null; then
                    break
                fi
                sleep 1
            done
            if kill -0 "$OLD_PID" 2>/dev/null; then
                echo "[$APP_NAME] Process did not stop gracefully, force killing..."
                kill -9 "$OLD_PID" 2>/dev/null || true
            fi
            echo "[$APP_NAME] Stopped."
        fi
        rm -f "$PID_FILE"
    fi

    # Also check if something else is holding port 8080
    PORT_PID=$(lsof -ti tcp:"$PORT" 2>/dev/null || true)
    if [ -n "$PORT_PID" ]; then
        echo "[$APP_NAME] Port $PORT in use by PID $PORT_PID — stopping it..."
        kill "$PORT_PID" 2>/dev/null || true
        sleep 2
        if kill -0 "$PORT_PID" 2>/dev/null; then
            kill -9 "$PORT_PID" 2>/dev/null || true
        fi
        echo "[$APP_NAME] Port $PORT freed."
    fi
}

build() {
    echo "[$APP_NAME] Building..."
    ./gradlew assemble -q
    echo "[$APP_NAME] Build complete."
}

start() {
    echo "[$APP_NAME] Starting on port $PORT..."
    ./gradlew bootRun &
    APP_PID=$!
    echo "$APP_PID" > "$PID_FILE"

    # Wait for the app to become healthy
    echo "[$APP_NAME] Waiting for application to be ready..."
    for i in $(seq 1 30); do
        if curl -sf "http://localhost:$PORT/actuator/health" > /dev/null 2>&1; then
            echo ""
            echo "[$APP_NAME] Application is up!"
            echo ""
            echo "  API Base   : http://localhost:$PORT/api/v1/work-tasks"
            echo "  H2 Console : http://localhost:$PORT/h2-console"
            echo "  Health     : http://localhost:$PORT/actuator/health"
            echo ""
            echo "[$APP_NAME] PID $APP_PID written to $PID_FILE"
            echo "[$APP_NAME] Run './start.sh stop' to stop the application."
            return 0
        fi
        printf "."
        sleep 2
    done

    echo ""
    echo "[$APP_NAME] WARNING: Application did not become healthy within 60s. Check logs."
}

case "${1:-start}" in
    stop)
        stop_if_running
        ;;
    restart)
        stop_if_running
        build
        start
        ;;
    *)
        stop_if_running
        build
        start
        ;;
esac
