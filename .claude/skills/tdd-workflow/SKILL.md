---
name: tdd-workflow
description: Use this skill when writing new features, fixing bugs, or refactoring code in Mirai. Enforces test-driven development with JUnit 5 + Testcontainers for backend and React Testing Library for frontend.
---

# Test-Driven Development Workflow

This skill ensures all code development follows TDD principles with comprehensive test coverage for the Mirai inventory management system.

## When to Activate

- Writing new features or functionality
- Fixing bugs or issues
- Refactoring existing code
- Adding REST API endpoints (Spring Boot)
- Creating React components (Next.js)
- Adding service layer business logic
- Implementing repository methods

## Core Principles

### 1. Tests BEFORE Code

ALWAYS write tests first, then implement code to make tests pass.

### 2. Coverage Requirements

- Minimum 90% coverage (unit + integration)
- All edge cases covered
- Error scenarios tested
- Boundary conditions verified

### 3. Test Types

#### Unit Tests

- Service layer business logic
- Utility functions and helpers
- JWT validation logic
- MapStruct mappers
- React component rendering

#### Integration Tests

- REST API endpoints with MockMvc
- Database operations with Testcontainers
- Full request/response cycles
- Authentication flows

---

## TDD Workflow Steps

### Step 1: Write User Journeys

```
As a [role], I want to [action], so that [benefit]

Example:
As a store manager, I want to add inventory to a box bin,
so that I can track stock levels accurately.
```

### Step 2: Generate Test Cases (Backend)

For each user journey, create comprehensive test cases:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BoxBinInventoryControllerIT extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void addInventory_returns201_validRequest() {
        // Test successful creation
    }

    @Test
    void addInventory_returns400_nullProductId() {
        // Test validation error
    }

    @Test
    void addInventory_returns404_boxBinNotFound() {
        // Test not found scenario
    }

    @Test
    void addInventory_returns409_duplicateInventory() {
        // Test conflict scenario
    }
}
```

### Step 3: Run Tests (They Should Fail)

```bash
./mvnw test -f services/inventory-service/pom.xml
# Tests should fail - we haven't implemented yet
```

### Step 4: Implement Code

Write minimal code to make tests pass:

```java
@Service
@Transactional
public class BoxBinInventoryService {

    public BoxBinInventory addInventory(UUID boxBinId, UUID productId, int quantity) {
        // Implementation guided by tests
    }
}
```

### Step 5: Run Tests Again

```bash
./mvnw test -f services/inventory-service/pom.xml
# Tests should now pass
```

### Step 6: Refactor

Improve code quality while keeping tests green:

- Remove duplication
- Improve naming
- Optimize performance
- Enhance readability

### Step 7: Verify Coverage

```bash
./mvnw test jacoco:report -f services/inventory-service/pom.xml
open services/inventory-service/target/site/jacoco/index.html
# Verify 90%+ coverage achieved
```

---

## Testing Patterns

### Unit Test Pattern (JUnit 5 + Mockito)

```java
@ExtendWith(MockitoExtension.class)
class BoxBinInventoryServiceTest {

    @Mock
    private BoxBinInventoryRepository repository;

    @Mock
    private BoxBinService boxBinService;

    @InjectMocks
    private BoxBinInventoryService service;

    @Test
    void addInventory_savesInventory_validInput() {
        // Given
        UUID boxBinId = UUID.randomUUID();
        BoxBin boxBin = BoxBin.builder().id(boxBinId).boxBinCode("B1").build();
        when(boxBinService.getBoxBinById(boxBinId)).thenReturn(boxBin);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        BoxBinInventory result = service.addInventory(boxBinId, UUID.randomUUID(), 10);

        // Then
        assertThat(result.getBoxBin()).isEqualTo(boxBin);
        assertThat(result.getQuantity()).isEqualTo(10);
        verify(repository).save(any(BoxBinInventory.class));
    }

    @Test
    void addInventory_throwsException_boxBinNotFound() {
        // Given
        UUID boxBinId = UUID.randomUUID();
        when(boxBinService.getBoxBinById(boxBinId))
            .thenThrow(new BoxBinNotFoundException(boxBinId));

        // When/Then
        assertThatThrownBy(() -> service.addInventory(boxBinId, UUID.randomUUID(), 10))
            .isInstanceOf(BoxBinNotFoundException.class);
    }
}
```

### Integration Test Pattern (MockMvc)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoxBinControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listBoxBins_returns200_empty() throws Exception {
        mockMvc.perform(get("/api/box-bins")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createBoxBin_returns201_validRequest() throws Exception {
        BoxBinRequestDTO request = new BoxBinRequestDTO("B1");

        mockMvc.perform(post("/api/box-bins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.boxBinCode").value("B1"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createBoxBin_returns400_nullCode() throws Exception {
        BoxBinRequestDTO request = new BoxBinRequestDTO(null);

        mockMvc.perform(post("/api/box-bins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
```

### JWT Service Unit Test Pattern

```java
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    private String testSecret = "test-secret-key-for-jwt-validation-that-is-long-enough";
    private Key signingKey;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", testSecret);
        signingKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validateToken_returnsTrue_validToken() {
        // Given
        String token = createValidToken("user-123", "John Doe", "admin");

        // When
        Boolean isValid = jwtService.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void validateToken_returnsFalse_expiredToken() {
        // Given
        String token = createExpiredToken("user-123");

        // When
        Boolean isValid = jwtService.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    // Helper methods for creating test tokens
    private String createValidToken(String personId, String name, String role) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("name", name);
        userMetadata.put("role", role);

        return Jwts.builder()
                .setSubject(personId)
                .claim("user_metadata", userMetadata)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();
    }
}
```

### Frontend Test Pattern (React Testing Library)

```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BoxBinList } from './BoxBinList'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } }
})

const wrapper = ({ children }) => (
  <QueryClientProvider client={queryClient}>
    {children}
  </QueryClientProvider>
)

describe('BoxBinList', () => {
  it('renders loading state initially', () => {
    render(<BoxBinList />, { wrapper })
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('renders box bins when data loads', async () => {
    render(<BoxBinList />, { wrapper })

    await waitFor(() => {
      expect(screen.getByText('B1')).toBeInTheDocument()
    })
  })

  it('handles empty state', async () => {
    render(<BoxBinList />, { wrapper })

    await waitFor(() => {
      expect(screen.getByText(/no box bins/i)).toBeInTheDocument()
    })
  })
})
```

---

## Test File Organization

### Backend (Spring Boot)

```
services/inventory-service/
└── src/
    ├── main/java/com/mirai/inventoryservice/
    │   ├── controllers/
    │   │   └── BoxBinController.java
    │   └── services/
    │       └── BoxBinService.java
    └── test/java/com/mirai/inventoryservice/
        ├── controllers/
        │   └── BoxBinControllerIT.java      # Integration tests
        ├── services/
        │   └── BoxBinServiceTest.java       # Unit tests
        └── BaseIntegrationTest.java         # Shared test config
```

### Frontend (Next.js)

```
apps/web/src/
├── components/
│   └── inventory/
│       ├── BoxBinList.tsx
│       └── BoxBinList.test.tsx
├── hooks/
│   └── queries/
│       ├── use-box-bins.ts
│       └── use-box-bins.test.ts
```

---

## Test Naming Convention

```java
@Test
void methodName_expectedBehavior_condition() {
    // Pattern: {what}_{expected outcome}_{when}
}

// Examples:
// listInventory_returns200_empty()
// addInventory_returns201_validRequest()
// addInventory_returns400_nullProductId()
// addInventory_throws404_boxBinNotFound()
// validateToken_returnsTrue_validToken()
// validateToken_returnsFalse_expiredToken()
```

---

## Mocking External Services

### Repository Mock

```java
@Mock
private BoxBinRepository boxBinRepository;

@Test
void findById_returnsBoxBin_exists() {
    // Given
    UUID id = UUID.randomUUID();
    BoxBin boxBin = BoxBin.builder().id(id).boxBinCode("B1").build();
    when(boxBinRepository.findById(id)).thenReturn(Optional.of(boxBin));

    // When
    BoxBin result = service.getBoxBinById(id);

    // Then
    assertThat(result).isEqualTo(boxBin);
}
```

### Supabase/External Service Mock

```java
@MockBean
private SupabaseAdminService supabaseAdminService;

@Test
void createUser_returns201_validRequest() throws Exception {
    // Given
    when(supabaseAdminService.createUser(any()))
        .thenReturn(new UserResponse("user-123", "test@example.com"));

    // When/Then
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
}
```

---

## Test Commands

### Backend (Maven)

```bash
# Run all tests
./mvnw test -f services/inventory-service/pom.xml

# Run specific test class
./mvnw test -Dtest="BoxBinControllerIT" -f services/inventory-service/pom.xml

# Run integration tests only
./mvnw test -Dtest="*IT" -f services/inventory-service/pom.xml

# Run unit tests only
./mvnw test -Dtest="*Test" -f services/inventory-service/pom.xml

# Run with coverage report
./mvnw test jacoco:report -f services/inventory-service/pom.xml
```

### Frontend (npm)

```bash
# Run all tests
npm test --prefix apps/web

# Run with coverage
npm test --prefix apps/web -- --coverage

# Run in watch mode
npm test --prefix apps/web -- --watch
```

---

## Coverage Thresholds

### Backend (JaCoCo)

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.90</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

### Frontend (Jest)

```json
{
  "jest": {
    "coverageThreshold": {
      "global": {
        "branches": 90,
        "functions": 90,
        "lines": 90,
        "statements": 90
      }
    }
  }
}
```

---

## Common Testing Mistakes to Avoid

### WRONG: Testing Implementation Details

```java
// Don't test private methods or internal state
assertThat(service.getInternalCache().size()).isEqualTo(5);
```

### CORRECT: Test Public Behavior

```java
// Test what the method returns or its side effects
BoxBin result = service.getBoxBinById(id);
assertThat(result.getBoxBinCode()).isEqualTo("B1");
```

### WRONG: Tests Depend on Each Other

```java
@Test
void createBoxBin() { /* creates B1 */ }

@Test
void updateBoxBin() { /* assumes B1 exists from previous test */ }
```

### CORRECT: Independent Tests

```java
@Test
void createBoxBin_returns201_validRequest() {
    BoxBinRequestDTO request = new BoxBinRequestDTO("B1");
    // Test creation
}

@Test
void updateBoxBin_returns200_existingBoxBin() {
    // Create box bin first within this test
    BoxBin boxBin = createTestBoxBin("B1");
    // Test update
}
```

### WRONG: No Cleanup

```java
@Test
void createBoxBin() {
    // Creates data but doesn't clean up
    repository.save(new BoxBin());
}
```

### CORRECT: Use @Transactional or Cleanup

```java
@Test
@Transactional  // Automatically rolls back after test
void createBoxBin_returns201_validRequest() {
    // Test logic
}
```

---

## Best Practices

1. **Write Tests First** - Always TDD
2. **One Assertion Focus** - Test single behavior per test
3. **Descriptive Names** - `methodName_expectedBehavior_condition()`
4. **Given-When-Then** - Clear test structure with comments
5. **Mock External Dependencies** - Isolate unit tests
6. **Test Edge Cases** - Null, empty, boundary values
7. **Test Error Paths** - Exceptions, 4xx/5xx responses
8. **Keep Tests Fast** - Unit tests < 50ms each
9. **Use @Transactional** - Auto-rollback database changes
10. **AssertJ over JUnit** - Fluent, readable assertions

---

## Success Metrics

- 90%+ code coverage achieved
- All tests passing (`./mvnw test`)
- No skipped or disabled tests
- Fast test execution (< 30s for unit tests)
- Tests catch bugs before production
- Test naming follows `methodName_expectedBehavior_condition()`

---

**Remember**: Tests are not optional. They are the safety net that enables confident refactoring, rapid development, and production reliability. Write tests first, make them pass, then refactor.

---

## Related Skills

- `project-guidelines/` - Mirai project architecture and patterns
