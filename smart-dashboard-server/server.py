
from flask import Flask, jsonify, request
from threading import Lock
import time

app = Flask(__name__)
lock = Lock()

state = {
    "music": {
        "playing": False,
        "title": "",
        "artist": "",
        "source": ""
    },
    "call": {
        "status": "ended",
        "caller": ""
    },
    "notification": {
        "active": False,
        "app": "",
        "sender": "",
        "text": "",
        "expires": 0
    }
}


def current_dashboard():
    now = time.time()

    with lock:
        music = dict(state["music"])
        call = dict(state["call"])
        notification = dict(state["notification"])

    if call["status"] in ("ringing", "active"):
        screen = "call"
    elif notification["active"] and now < notification["expires"]:
        screen = "notification"
    elif music["playing"]:
        screen = "music"
    else:
        screen = "clock"

    return {
        "screen": screen,
        "time": int(now),
        "music": music,
        "call": call,
        "notification": notification
    }


@app.get("/")
def home():
    return {
        "server": "ESP32 Smart Dashboard",
        "status": "online",
        "state_endpoint": "/api/state",
        "event_endpoint": "/api/event"
    }


@app.get("/api/state")
def get_state():
    return jsonify(current_dashboard())


@app.post("/api/event")
def update_event():
    data = request.get_json(silent=True)

    if not isinstance(data, dict):
        return jsonify({"error": "Send a JSON object"}), 400

    event = data.get("event")

    with lock:
        if event == "music":
            playing = data.get("playing", False)
            if not isinstance(playing, bool):
                return jsonify({"error": "playing must be true or false"}), 400

            state["music"] = {
                "playing": playing,
                "title": str(data.get("title", ""))[:120],
                "artist": str(data.get("artist", ""))[:120],
                "source": str(data.get("source", ""))[:40]
            }

        elif event == "call":
            status = data.get("status", "ended")
            if status not in ("ringing", "active", "ended"):
                return jsonify({"error": "Invalid call status"}), 400

            state["call"] = {
                "status": status,
                "caller": str(data.get("caller", ""))[:100]
            }

        elif event == "notification":
            state["notification"] = {
                "active": True,
                "app": str(data.get("app", "Notification"))[:60],
                "sender": str(data.get("sender", ""))[:100],
                "text": str(data.get("text", ""))[:300],
                "expires": time.time() + 5
            }

        else:
            return jsonify({
                "error": "event must be music, call, or notification"
            }), 400

    return jsonify({
        "ok": True,
        "dashboard": current_dashboard()
    })


if __name__ == "__main__":
    print("Smart Dashboard server starting on port 5000")
    app.run(host="0.0.0.0", port=5000, debug=False)
