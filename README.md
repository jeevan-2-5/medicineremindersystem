# Medicine Reminder System (Mini Project)

**A simple, client-side medicine reminder app built with HTML, CSS, Java and JavaScript.**
This project helps users schedule and receive timely reminders for prescribed medicines via on-screen popups and browser notifications, keeps a persistent intake history, and can notify a caregiver or relative if a dose is missed. The app can also export intake history to help users consult with their doctor.

<img width="1080" height="1920" alt="image" src="https://github.com/user-attachments/assets/c21ede10-d8fb-4ccf-9b3e-a13c43707f1c" />
<img width="1080" height="1920" alt="image" src="https://github.com/user-attachments/assets/2ce7dacb-5829-49aa-b842-db738c313ea9" />
<img width="1080" height="1920" alt="image" src="https://github.com/user-attachments/assets/8c75cdd1-497e-4c3c-bc26-5915fac4b742" />
---

## Table of Contents

* [Features](#features)
* [Tech Stack](#tech-stack)
* [Usage](#usage)
* [How it works (high level)](#how-it-works-high-level)
* [Data model & storage](#data-model--storage)
* [Caretaker alerts & doctor consultation](#caretaker-alerts--doctor-consultation)
* [Contributing](#contributing)
* [Managed](#managed)

---

## Features

* Add, edit and remove medicines with dose, frequency and schedule.
* On-time reminders with popup dialogs and browser notifications.
* Intake history (what was taken, when, missed doses) persisted across sessions.
* Automatic alert to a caregiver/relative when a scheduled dose is missed.
* Export / download intake history (CSV) for doctor consultations.
* Simple, responsive UI (HTML + CSS) and vanilla JavaScript logic.

---

## Tech Stack

* HTML5
* CSS3 (plain CSS or a framework if you like)
* JavaScript (ES6+)
* Browser APIs used: `localStorage`, `Notification` API, `setInterval` / `setTimeout`
* Service Worker (for background notifications / PWA behavior)
* Simple backend (Node/Express) + third-party SMS/email provider (e.g., Twilio, SendGrid) to send caretaker alerts.

---

## Usage

1. Click **Add Medicine** and fill in:

   * Medicine name
   * Dosage
   * Time(s) or frequency (e.g., daily at 09:00, or every 8 hours)
   * Number of days or an end date
   * Optional: caretaker contact (phone or email)

2. The app stores each scheduled event and checks (every minute) for reminders to show.

3. When it's time: the app shows a popup and fires a browser notification.

4. Mark the dose **Taken** or **Missed** in the popup. The app logs the result to history.

5. If the dose is marked as missed or not acknowledged within a configurable grace period, the app triggers an alert to the caregiver (if configured).

6. Export intake history from the History screen as a CSV to share with a doctor.

---

## How it works (high level)

* Schedule representation: each medicine entry expands into scheduled events (timestamped occurrences).
* Reminder loop: a lightweight scheduler runs in the browser (e.g., `setInterval` every 30–60 seconds) and compares current time to scheduled occurrences.
* Notifications:

  * Uses the `Notification` API for system-level notifications (requires permission).
  * Also shows an in-app modal/popup as a fallback.
* Persistence: all medicines and history are saved to `localStorage` as JSON so they persist across page reloads.

**Note:** Browser tabs can be suspended by the OS or the browser; for reliable background alerts consider turning this into a Progressive Web App with a Service Worker or adding a small backend scheduler.

---

## Data model & storage

Example simplified data structures (stored in `localStorage`):

```js
// medicines key: stores active reminders
medicines = [
  {
    id: 'uuid-1',
    name: 'Aspirin',
    dosage: '100 mg',
    schedule: [ '2026-01-08T09:00:00', '2026-01-08T21:00:00', ... ],
    caretaker: { name: 'Ravi', phone: '+911234567890', email: 'ravi@example.com' }
  }
]

// history key: records each reminder result
history = [
  { medicineId: 'uuid-1', time: '2026-01-08T09:00:00', status: 'taken', notedAt: '2026-01-08T09:01:10' },
  { medicineId: 'uuid-1', time: '2026-01-08T21:00:00', status: 'missed' }
]
```

---

## Caretaker alerts & doctor consultation

### Sending alerts to a caregiver

Because client-side code cannot reliably send SMS/email without exposing credentials, the recommended approach is:

1. Build a tiny backend endpoint (e.g., `POST /api/alert`) that accepts `{ caregiver, medicine, scheduledTime, userInfo }`.
2. The backend calls an SMS/email provider (Twilio, MessageBird, SendGrid, etc.) using server-side credentials to deliver the message.

**Example client-side call**:

```js
fetch('/api/alert', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ caretaker, medicine, scheduledTime })
})
```

**Example backend responsibilities**:

* Validate the request and rate-limit alerts.
* Store logs of alerts sent.
* Use secure environment variables for provider keys.

### Exporting intake history for doctor consultation

Provide an **Export CSV** button on the History page which converts the `history` array to CSV and triggers a download. Example fields: `date,time,medicine,dosage,status,notedAt`.

---

## Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature-name`)
3. Commit your changes (`git commit -m 'Add feature'`)
4. Push and create a PR

Please include tests and keep UI accessible.

Managed by :

[https://github.com/jeevan-2-5](https://github.com/jeevan-2-5)

[https://github.com/Decentketan01](https://github.com/Decentketan01)

---
