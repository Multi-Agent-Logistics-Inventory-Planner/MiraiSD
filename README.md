# Mirai Inventory Management - Setup Guide

Quick setup instructions to get the project running on your machine.

## Prerequisites

Before you start, make sure you have installed:

- **Docker Desktop** (includes Docker Compose)
  - Download from: https://www.docker.com/products/docker-desktop
  - Make sure Docker is running before proceeding
- **Java 17+** and **Maven** (for building the inventory service)
  - Check with: `java -version` and `mvn -version`
- **Node.js 20+** and **npm** (for frontend development)
  - Check with: `node -v` and `npm -v`
- **Python 3.10+** (for forecasting service)
  - Check with: `python --version` or `python3 --version`

---

## Step 1: Clone and Navigate to Project

```bash
git clone <repository-url>
cd mirai
```

---

## Step 2: Set Up Environment Variables

1. Copy the environment template:
   ```bash
   cp env.template .env
   ```

2. Edit the `.env` file and fill in your Supabase credentials:
   ```
   SUPABASE_DB_URL=jdbc:postgresql://db.xxxxxxxxxx.supabase.co:6543/postgres?pgbouncer=true
   SUPABASE_DB_USERNAME=postgres
   SUPABASE_DB_PASSWORD=your-actual-password
   ```

   **Important:** Use the **Session Pooler** connection string (port 6543) instead of direct connection (port 5432) for IPv4 compatibility in Docker.
   
   To get the correct connection string:
   - Go to Supabase Dashboard → Settings → Database → Connection Pooling
   - Copy the connection string from "Session mode"
   - It should include `:6543` port and `pgbouncer=true` parameter

   **Note:** Ask a teammate for the actual Supabase credentials if you don't have them.

---

## Step 3: Build the Inventory Service (Java)

The inventory service needs to be built with Maven before Docker can use it:

```bash
cd services/inventory-service
mvn clean package -DskipTests
cd ../..
```

This will create a JAR file that Docker will use.

---

## Step 4: Start Everything with Docker Compose

From the project root directory:

```bash
docker-compose -f infra/docker-compose.yml up --build
```

This will start:
- **Inventory Service** (Spring Boot API) - http://localhost:4000
- **Frontend** (Next.js) - http://localhost:3000
- **Kafka** (Message broker) - localhost:9092
- **Kafka UI** (Web interface for Kafka) - http://localhost:8080

**First time startup might take 5-10 minutes** while Docker downloads images and builds everything.

To run in the background (detached mode):
```bash
docker-compose -f infra/docker-compose.yml up -d
```

To stop everything:
```bash
docker-compose -f infra/docker-compose.yml down
```

---

## Step 5: Set Up Forecasting Service (Python)

The forecasting service isn't in Docker yet, so run it locally:

1. Navigate to the forecasting service:
   ```bash
   cd services/forecasting-service
   ```

2. Create a Python virtual environment:
   ```bash
   python -m venv venv
   ```

3. Activate the virtual environment:
   - **Windows:**
     ```bash
     venv\Scripts\activate
     ```
   - **Mac/Linux:**
     ```bash
     source venv/bin/activate
     ```

4. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

5. Run the forecasting service (when needed):
   ```bash
   python -m forecastingPipeline.src.forecast_job
   ```
   Or use the specific script you need from the `src/` folder.

---

## Verifying Everything Works

Once all services are running, check:

1. **Frontend**: Open http://localhost:3000 in your browser
2. **Inventory API**: Visit http://localhost:4000 (should see Spring Boot running)
3. **Kafka UI**: Open http://localhost:8080 to see Kafka topics and messages
4. **Kafka**: Ensure it's running with no errors in the Docker logs

---

## Useful Commands

### Docker Commands
```bash
# View logs for all services
docker-compose -f infra/docker-compose.yml logs -f

# View logs for specific service
docker-compose -f infra/docker-compose.yml logs -f inventory-service
docker-compose -f infra/docker-compose.yml logs -f frontend

# Restart a specific service
docker-compose -f infra/docker-compose.yml restart inventory-service

# Rebuild after code changes
docker-compose -f infra/docker-compose.yml up --build

# Stop and remove all containers
docker-compose -f infra/docker-compose.yml down

# Stop and remove everything including volumes
docker-compose -f infra/docker-compose.yml down -v
```

### Development Without Docker

If you want to run services individually for development:

**Frontend:**
```bash
cd apps/web
npm install
npm run dev
# Runs on http://localhost:3000
```

**Inventory Service:**
```bash
cd services/inventory-service
mvn spring-boot:run
# Runs on http://localhost:4000
```

---

## Troubleshooting

**Docker won't start:**
- Make sure Docker Desktop is running
- Check if ports 3000, 4000, 8080, 9092 are not already in use

**Database connection errors:**
- Verify your `.env` file has correct Supabase credentials
- **Use Session Pooler (port 6543)** instead of direct connection (port 5432) for IPv4 compatibility
- If you see "Connection refused", make sure you're using port 6543 with `pgbouncer=true`
- Check if your IP is whitelisted in Supabase dashboard

**"Port already in use" errors:**
```bash
# Find what's using a port (example for port 4000)
# Windows:
netstat -ano | findstr :4000

# Mac/Linux:
lsof -i :4000

# Then stop that process or change the port in infra/docker-compose.yml
```

**Forecasting service Prophet installation issues:**
- Prophet requires additional system dependencies
- Windows: May need to install Visual C++ Build Tools
- Mac: Might need `brew install gcc`
- Linux: Might need `sudo apt-get install build-essential`

**Maven build fails:**
- Make sure you have Java 17 or higher
- Try `mvn clean install -U` to force update dependencies

---

## Project Structure Quick Reference

```
MiraiSD/
├── apps/
│   └── web/                       # Next.js web app (TypeScript/React)
├── services/
│   ├── inventory-service/         # Spring Boot API (Java)
│   └── forecasting-service/       # Forecasting pipeline (Python)
├── infra/
│   └── docker-compose.yml         # Docker orchestration
├── scripts/
│   └── get-token.mjs              # Supabase auth token helper
├── .context/                      # AI/Claude context documentation
└── .env                           # Environment variables (create from env.template)
```