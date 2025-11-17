# Mirai Inventory Management System

A comprehensive inventory management system for tracking products across claw machines, bins, and shelves with full audit trails and ML-ready event streaming.

---

## 🚀 Quick Start

### Prerequisites
- Java 21
- Maven 3.6+
- Supabase account (PostgreSQL database)

### Setup

1. **Clone the repository**
   ```bash
   git clone <your-repo-url>
   cd mirai-inventory
   ```

2. **Configure environment variables**
   ```bash
   cp env.template .env
   # Edit .env with your Supabase credentials
   ```

3. **Build the project**
   ```bash
   cd inventory-service
   ./mvnw clean install
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

5. **Access the API**
   ```
   http://localhost:4000/api
   ```

---

## 📁 Project Structure

```
mirai-inventory/
├── inventory-service/          # Spring Boot backend
│   ├── src/main/java/
│   │   └── com/pm/inventoryservice/
│   │       ├── controllers/    # REST API endpoints
│   │       ├── services/       # Business logic
│   │       ├── repositories/   # Data access layer
│   │       ├── models/         # JPA entities
│   │       ├── dtos/           # Request/Response DTOs
│   │       ├── exceptions/     # Custom exceptions
│   │       └── config/         # Configuration
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
├── .env                        # Environment variables (gitignored)
├── env.template                # Environment template
└── .gitignore

NOT IN GIT (local only):
├── http-requests/              # HTTP test files (gitignored)
├── refs/                       # Documentation (gitignored)
└── STOCK_MOVEMENT_IMPLEMENTATION.md (gitignored)
```

---

## 🔧 Technologies

- **Backend**: Spring Boot 3.x, Java 21
- **Database**: PostgreSQL (Supabase hosted)
- **ORM**: JPA/Hibernate
- **Build Tool**: Maven
- **Event Streaming**: Kafka (planned)
- **Authentication**: Supabase Auth (planned)

---

## 📊 Database Schema

### Storage Locations
- `machines` - Claw machines (B1, B2, ...)
- `bins` - Overhead storage (S1, S2, ...)
- `shelves` - Back room storage (R1, R2, ...)

### Inventory
- `machine_inventory` - Items in machines
- `bin_inventory` - Items in bins (plushies/keychains only)
- `shelf_inventory` - Items on shelves

### Audit & Events
- `stock_movements` - Immutable audit trail of all inventory changes
- `users` - User accounts (synced from Supabase Auth)
- `event_outbox` - Transactional outbox for Kafka events
- `notifications` - Low-stock and reorder alerts
- `forecast_predictions` - ML-generated forecasts

---

## 🎯 API Endpoints

### Users
- `GET    /api/users` - List all users
- `GET    /api/users/{id}` - Get user by ID
- `POST   /api/users` - Create user
- `PUT    /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

### Machines
- `GET    /api/machines` - List all machines
- `POST   /api/machines` - Create machine
- `PUT    /api/machines/{id}` - Update machine
- `DELETE /api/machines/{id}` - Delete machine

### Machine Inventory
- `GET    /api/machines/{machineId}/inventory` - List items
- `POST   /api/machines/{machineId}/inventory` - Add item
- `GET    /api/machines/{machineId}/inventory/{id}` - Get item
- `PUT    /api/machines/{machineId}/inventory/{id}` - Update item
- `DELETE /api/machines/{machineId}/inventory/{id}` - Delete item

### Bins (same pattern as Machines)
- `GET/POST/PUT/DELETE /api/bins/**`
- `GET/POST/PUT/DELETE /api/bins/{binId}/inventory/**`

### Shelves (same pattern as Machines)
- `GET/POST/PUT/DELETE /api/shelves/**`
- `GET/POST/PUT/DELETE /api/shelves/{shelfId}/inventory/**`

### Stock Movements
- `POST  /api/stock-movements/{locationType}/{inventoryId}/adjust` - Adjust quantity
- `POST  /api/stock-movements/transfer` - Transfer between locations
- `GET   /api/stock-movements/history/{itemId}` - Get movement history

---

## 🔐 Environment Variables

Required environment variables (set in `.env`):

```bash
# Database
SUPABASE_DB_URL=jdbc:postgresql://db.xxx.supabase.co:5432/postgres
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=your-password

# Optional: Kafka (when implementing)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_TOPIC_INVENTORY_CHANGES=inventory-changes
```

---

## ✅ Current Implementation Status

### Completed ✅
- [x] All domain entities (User, Machine, Bin, Shelf, Inventory models)
- [x] All enums (ProductCategory, LocationType, StockMovementReason, etc.)
- [x] Full CRUD for Users, Machines, Bins, Shelves
- [x] Full CRUD for MachineInventory, BinInventory, ShelfInventory
- [x] Stock movement workflow (adjust, transfer, history)
- [x] Complete validation and exception handling
- [x] PostgreSQL integration with Supabase
- [x] Database migration completed

### In Progress 🚧
- [ ] Event Outbox + Kafka integration
- [ ] Notification service
- [ ] Forecast service
- [ ] Supabase JWT authentication

### Planned 📋
- [ ] Docker containerization
- [ ] Unit & integration tests
- [ ] CI/CD pipeline
- [ ] Frontend integration

---

## 🐳 Docker Deployment (Planned)

The application will be containerized with:
- `inventory-service` - Spring Boot backend
- `kafka` - Event streaming
- `zookeeper` - Kafka coordination
- `frontend` - UI application

Database (PostgreSQL) remains hosted on Supabase cloud.

---

## 📚 Documentation

- `GIT_TRANSFER_GUIDE.md` - Guide for transferring project between machines
- `STOCK_MOVEMENT_IMPLEMENTATION.md` - Stock movement workflow documentation
- `refs/normalized-inventory-system.plan.md` - Complete implementation plan

**Note**: Documentation files are gitignored. Recreate them from commit history if needed.

---

## 🤝 Contributing

This is a group project for inventory management. Team members:
- Backend (Java/Spring Boot): You
- ML/Forecasting (Python): Teammate
- Frontend: TBD

---

## 📝 License

Private project for educational/business purposes.

---

## 🆘 Troubleshooting

### Application won't start
- Check that `.env` file exists with correct Supabase credentials
- Verify Supabase database is accessible
- Check port 4000 is not in use

### Database connection error
- Verify `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD` in `.env`
- Check Supabase database is running
- Verify network/firewall settings

### Build failures
- Ensure Java 21 is installed: `java -version`
- Clear Maven cache: `./mvnw clean`
- Delete `target/` directory and rebuild

---

## 📧 Contact

For questions or issues, contact the project team.
