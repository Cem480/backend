# ARES-X — CS-458 Project 2


---

## Repository Structure

```
ares-x-frontend/      → React Native Expo app (Web Architect admin + Mobile Client)
backend/              → Spring Boot 3.2.3 REST API + WebSocket
appium-tests/         → Appium 8.6.0 + TestNG mobile test suite
```

Source code is in three separate GitHub repositories:
- `Cem480/ares-x-frontend`
- `Cem480/backend`
- `Cem480/appium-tests`

---

## Prerequisites

Install the following before running anything:

| Tool | Version | Download |
|------|---------|----------|
| Node.js | 18+ | https://nodejs.org |
| npm | 9+ | bundled with Node |
| Java JDK | 17 | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org |
| MongoDB | 7.x | https://www.mongodb.com/try/download/community |
| Android Studio | Latest | https://developer.android.com/studio |
| Appium | 8.6.0 | `npm install -g appium` |
| Appium UiAutomator2 Driver | Latest | `appium driver install uiautomator2` |
| Expo Go (on emulator) | Latest | Install via Android Studio AVD |

---

## 1 — Backend (Spring Boot)

### Setup

```bash
cd backend
```

Open `src/main/resources/application.properties` and confirm:

```properties
spring.data.mongodb.uri=mongodb://localhost:27017/ares_x
server.port=8080
jwt.secret=your-secret-key
```

MongoDB must be running locally on port 27017 before starting the backend.

### Start MongoDB

```bash
# macOS / Linux
mongod --dbpath /data/db

# Windows (run as Administrator)
net start MongoDB
```

### Run the Backend

```bash
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

To verify it is running:

```bash
curl http://localhost:8080/api/auth/health
# Expected: 200 OK
```

---

## 2 — Frontend (React Native Expo)

### Setup

```bash
cd ares-x-frontend
npm install
```

### Run as Web (Admin / Web Architect)

```bash
npx expo start --web
```

Opens at `http://localhost:8081` in your browser. This is the survey designer interface used by the Admin actor.

### Run as Android (Mobile Client)

Start an Android Emulator in Android Studio first (API 33, Pixel 6 recommended), then:

```bash
npx expo start --android
```

Expo Go will open on the emulator and load the mobile client automatically.

> **Note:** The mobile client connects to the backend at `http://10.0.2.2:8080` — this is the Android emulator's alias for `localhost`. No configuration change is needed.

### Run Frontend Unit Tests (TDD Suite — 33 tests)

```bash
cd ares-x-frontend
npm test
```

Expected output:

```
Test Suites: 6 passed, 6 total
Tests:       33 passed, 33 total
```

Test files are located at:
- `__tests__/algorithms/RCLR.test.js`
- `__tests__/algorithms/GBCR.test.js`
- `__tests__/components/SurveyFooter.test.jsx`
- `__tests__/components/MCQQuestion.test.jsx`
- `__tests__/components/RatingQuestion.test.jsx`
- `__tests__/components/OpenQuestion.test.jsx`

---

## 3 — Appium Tests (TC01–TC10 + SYNC01)

### Setup

```bash
cd appium-tests
```

Open `src/test/java/com/aresx/tests/BaseTest.java` and confirm these constants match your environment:

```java
static final String BASE_URL     = "http://10.0.2.2:8080";
static final String APPIUM_URL   = "http://127.0.0.1:4723";
static final String APP_PACKAGE  = "host.exp.exponent";
static final String TEST_EMAIL   = "admin@ares-x.com";
static final String TEST_PASSWORD= "admin123";
```

### Prerequisites Before Running

Make sure all three of the following are running simultaneously:

1. **MongoDB** — running on port 27017
2. **Backend** — `mvn spring-boot:run` in `/backend`
3. **Android Emulator** — API 33, with Expo Go installed and the ARES-X mobile bundle loaded
4. **Appium Server** — start in a separate terminal:

```bash
appium --port 4723
```

### Run All Appium Tests

```bash
cd appium-tests
mvn test
```

This runs TC01 through TC10 and SYNC01 in sequence via TestNG.

### Run a Single Test Class

```bash
mvn test -Dtest=SurveyLifecycleTest        # TC01–TC03
mvn test -Dtest=RCLRVisibilityTest         # TC04–TC08
mvn test -Dtest=GBCRConflictTest           # TC09
mvn test -Dtest=SubmitTest                 # TC10
mvn test -Dtest=SyncConflictTest           # SYNC01
```


## Test User Credentials

| Email | Password | Role | Used In |
|-------|----------|------|---------|
| admin@ares-x.com | admin123 | Admin + MobileUser | All TCs, SYNC01 |

The test user is created automatically by the Appium suite's `@BeforeSuite` setup method via REST Assured if it does not already exist in MongoDB.

---
