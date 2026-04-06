# 01. Spring Initializr 프로젝트 구조 분석

> Spring Initializr(start.spring.io)로 생성된 프로젝트의 전체 구조를 분석하고,
> 각 파일과 설정이 무엇을 의미하는지 NestJS와 비교하며 설명합니다.

---

## 생성 시 선택한 설정

| 항목 | 선택값 | 설명 |
|------|--------|------|
| Project | Gradle - Kotlin | 빌드 도구. Gradle을 Kotlin DSL로 설정 |
| Language | Java | 개발 언어 |
| Spring Boot | 4.0.5 | 프레임워크 버전 |
| Packaging | Jar | 배포 패키징 형식 |
| Configuration | Properties | 설정 파일 형식 (`application.properties`) |
| Java | 21 | JDK 버전 (LTS 버전, 설치된 JDK에 맞게 17→21로 변경) |
| Dependencies | Lombok, Spring Web, Validation | 추가 라이브러리 |

> **[NestJS 비교]**
> NestJS는 `npm init` 또는 `nest new` 명령으로 프로젝트를 생성한다.
> Spring Initializr는 이에 대응하는 Spring의 공식 프로젝트 생성 도구로,
> 의존성 선택부터 빌드 설정까지 자동으로 구성해준다.

---

## 전체 디렉토리 구조

```
todo-spring/                          ← 프로젝트 루트
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/study/todo_spring/
│   │   │       └── TodoSpringApplication.java  ← 앱 진입점
│   │   └── resources/
│   │       ├── application.properties          ← 설정 파일
│   │       ├── static/                         ← 정적 파일 (CSS, JS, 이미지)
│   │       └── templates/                      ← 서버사이드 템플릿 (Thymeleaf 등)
│   └── test/
│       └── java/
│           └── com/study/todo_spring/
│               └── TodoSpringApplicationTests.java  ← 기본 테스트
│
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar          ← Gradle Wrapper 실행 바이너리
│       └── gradle-wrapper.properties   ← Gradle 버전 명시
│
├── build.gradle.kts    ← 빌드 설정 + 의존성 (NestJS의 package.json)
├── settings.gradle.kts ← 프로젝트 이름 설정 (단일 모듈)
├── gradlew             ← Linux/Mac용 Gradle Wrapper 실행 스크립트
├── gradlew.bat         ← Windows용 Gradle Wrapper 실행 스크립트
└── HELP.md             ← Spring Initializr가 자동 생성한 참고 문서
```

---

## 파일별 상세 설명

### 1. `build.gradle.kts` — 빌드 설정 파일

> **[NestJS 비교]** NestJS의 `package.json`에 대응한다.
> 의존성 목록, 빌드 방법, Java 버전 등을 선언하는 프로젝트의 핵심 설정 파일이다.

```kotlin
plugins {
    java                                                    // Java 컴파일 지원
    id("org.springframework.boot") version "4.0.5"         // Spring Boot 플러그인
    id("io.spring.dependency-management") version "1.1.7"  // 의존성 버전 자동 관리
}
```

**플러그인 설명:**

| 플러그인 | 역할 | NestJS 대응 |
|---------|------|------------|
| `java` | Java 소스 컴파일, JAR 패키징 | (기본 빌드 기능) |
| `spring-boot` | `bootRun`(개발 서버 실행), 실행 가능한 fat-JAR 생성 | `ts-node` + `nest start` |
| `dependency-management` | Spring BOM을 통해 의존성 버전 충돌 자동 해결 | npm의 peer dependency 관리 |

```kotlin
group = "com.study"        // 패키지 그룹 ID (회사/팀 식별자, NestJS엔 없는 개념)
version = "0.0.1-SNAPSHOT" // 현재 버전 (SNAPSHOT = 개발 중인 버전)

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17) // JDK 17 사용 명시
    }
}
```

```kotlin
repositories {
    mavenCentral() // 의존성을 Maven Central에서 다운로드 (NestJS의 npm registry 역할)
}
```

**의존성(dependencies) 상세:**

```kotlin
dependencies {
    // ── 런타임 의존성 (implementation) ──────────────────────────────────────
    
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Bean Validation (Jakarta Validation) 포함.
    // @NotNull, @Size, @Email 등의 유효성 검사 어노테이션 사용 가능.
    // NestJS의 class-validator + ValidationPipe 에 대응.

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Spring MVC = REST API 서버 핵심 모듈.
    // 내장 Tomcat 서버 포함 → 별도 서버 설치 없이 실행 가능.
    // NestJS의 @nestjs/core + @nestjs/platform-express 에 대응.

    // ── 컴파일 타임만 사용 (compileOnly) ──────────────────────────────────
    
    compileOnly("org.projectlombok:lombok")
    // Lombok: 반복적인 boilerplate 코드를 어노테이션으로 자동 생성.
    // @Getter, @Setter, @NoArgsConstructor, @Builder 등.
    // 컴파일 시 코드 생성 후 런타임엔 필요 없으므로 compileOnly.
    // NestJS/TypeScript엔 직접 대응 개념 없음 (TypeScript가 언어 수준에서 해결).

    annotationProcessor("org.projectlombok:lombok")
    // Lombok이 어노테이션을 처리해 코드를 생성하도록 Java 컴파일러에 등록.
    // compileOnly만으로는 부족하고 annotationProcessor도 함께 필요.

    // ── 테스트 의존성 ──────────────────────────────────────────────────────
    
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // 테스트용 Spring 컨텍스트 + MockMvc 포함.
    // NestJS의 @nestjs/testing 에 대응.

    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    // 테스트 코드에서도 Lombok 사용 가능하도록 설정.

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // JUnit 5 테스트 실행 엔진. Gradle이 테스트를 실행할 때 필요.
    // NestJS의 jest-runner 에 대응.
}
```

**의존성 스코프 비교:**

| Gradle 스코프 | npm 대응 | 사용 시점 |
|--------------|---------|---------|
| `implementation` | `dependencies` | 컴파일 + 런타임 |
| `compileOnly` | `devDependencies` (빌드 도구) | 컴파일만, 실행 JAR에 미포함 |
| `testImplementation` | `devDependencies` | 테스트 컴파일 + 런타임 |
| `testRuntimeOnly` | `devDependencies` | 테스트 실행 시만 |
| `annotationProcessor` | 없음 (babel plugin 유사) | 컴파일 시 코드 생성 |

```kotlin
tasks.withType<Test> {
    useJUnitPlatform() // 테스트 프레임워크로 JUnit 5 사용 선언
}
```

---

### 2. `settings.gradle.kts` — 프로젝트 이름 설정

```kotlin
rootProject.name = "todo-spring"
```

> 현재는 단일 모듈 프로젝트이므로 프로젝트 이름만 선언한다.
> 멀티 모듈 프로젝트(모노레포 구조)로 확장할 경우 여기에 하위 모듈을 추가한다.
> NestJS의 `nest-cli.json` 또는 모노레포의 `workspace` 설정과 유사한 역할.

---

### 3. `TodoSpringApplication.java` — 앱 진입점

```java
package com.study.todo_spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication  // ← 이 어노테이션 하나가 핵심
public class TodoSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoSpringApplication.class, args);
    }
}
```

> **[NestJS 비교]**
> NestJS의 `main.ts`에 대응한다.
>
> ```typescript
> // NestJS main.ts
> async function bootstrap() {
>   const app = await NestFactory.create(AppModule);
>   await app.listen(3000);
> }
> bootstrap();
> ```
>
> Spring에서는 `SpringApplication.run()`이 동일한 역할을 한다.
> 내장 Tomcat을 시작하고, Spring IoC 컨테이너(ApplicationContext)를 초기화한다.

**`@SpringBootApplication` 의 정체:**

이 어노테이션 하나는 실제로 3가지 어노테이션의 조합이다.

```java
@SpringBootConfiguration  // = @Configuration: 이 클래스가 Bean 설정 클래스임을 선언
@EnableAutoConfiguration  // 클래스패스를 스캔해 필요한 설정을 자동으로 적용
@ComponentScan            // 같은 패키지(com.study.todo_spring) 이하를 스캔해 Bean 등록
```

| 어노테이션 | 역할 |
|-----------|------|
| `@SpringBootConfiguration` | `@Bean`을 등록할 수 있는 설정 클래스 선언 |
| `@EnableAutoConfiguration` | `spring-boot-starter-webmvc` 감지 → Tomcat + MVC 자동 설정 |
| `@ComponentScan` | `@Controller`, `@Service`, `@Repository` 클래스를 자동 감지해 Bean으로 등록 |

> **[NestJS 비교]** `@ComponentScan`은 NestJS의 `imports: [Module]`과 유사하다.
> NestJS는 명시적으로 Module에 등록해야 하지만,
> Spring은 패키지를 자동 스캔해서 어노테이션이 붙은 클래스를 자동으로 Bean으로 등록한다.
> 이것이 Spring의 **Convention over Configuration(설정보다 관례)** 철학이다.

---

### 4. `application.properties` — 애플리케이션 설정 파일

```properties
spring.application.name=todo-spring
```

> 현재는 앱 이름만 설정되어 있다.
> 이후 DB 연결, 서버 포트, 로깅 레벨 등 모든 설정이 여기에 추가된다.

> **[NestJS 비교]** NestJS의 `.env` + `ConfigModule`에 대응한다.
> Spring은 `application.properties`(또는 `application.yml`) 하나로 모든 설정을 관리한다.
> 환경별로 `application-dev.properties`, `application-prod.properties`를 만들고
> 프로파일(`spring.profiles.active=dev`)로 전환할 수 있다.

**앞으로 추가될 설정 예시:**

```properties
# 서버 포트 (기본값: 8080)
server.port=8080

# MySQL 연결 설정 (Phase 2에서 추가)
spring.datasource.url=jdbc:mysql://localhost:3306/todo_db
spring.datasource.username=root
spring.datasource.password=secret

# JPA 설정 (Phase 2에서 추가)
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# 로깅 레벨
logging.level.com.study.todo_spring=DEBUG
```

---

### 5. `src/main/resources/static/` — 정적 파일 디렉토리

Spring MVC가 기본으로 제공하는 정적 파일 서빙 경로.
이 폴더에 `index.html`, CSS, JS를 넣으면 자동으로 서빙된다.
REST API 백엔드로만 사용할 경우 비워두면 된다.

> **[NestJS 비교]** `ServeStaticModule`의 `rootPath` 설정과 동일한 역할.

---

### 6. `src/main/resources/templates/` — 서버사이드 템플릿 디렉토리

Thymeleaf, Freemarker 등 서버사이드 렌더링(SSR) 엔진의 템플릿 파일 위치.
REST API 백엔드로만 사용할 경우 비워두면 된다.

---

### 7. `TodoSpringApplicationTests.java` — 기본 테스트

```java
@SpringBootTest  // 전체 Spring ApplicationContext를 로드해서 테스트
class TodoSpringApplicationTests {

    @Test
    void contextLoads() {
        // Spring 컨텍스트가 에러 없이 로드되는지 확인하는 smoke test
        // 설정 오류, Bean 충돌 등이 있으면 여기서 먼저 감지된다
    }
}
```

> **[NestJS 비교]** NestJS의 `app.e2e-spec.ts` 또는 `Test.createTestingModule()` 기반의
> 통합 테스트와 유사하다. `@SpringBootTest`는 실제 서버와 동일한 컨텍스트를 띄워
> 통합 테스트를 수행한다.

---

### 8. `gradle/wrapper/` — Gradle Wrapper

**역할:** 팀원/CI 환경에 Gradle이 설치되어 있지 않아도 동일한 버전으로 빌드를 실행할 수 있게 해준다.

```properties
# gradle-wrapper.properties
distributionUrl=https://services.gradle.org/distributions/gradle-9.4.1-bin.zip
```

이 프로젝트는 **Gradle 9.4.1** 을 사용한다.
`./gradlew` 실행 시 지정된 버전의 Gradle을 자동으로 다운로드 후 실행한다.

> **[NestJS 비교]** npm은 Node.js 버전이 보장되지 않지만,
> Gradle Wrapper는 프로젝트에 Gradle 버전을 고정시켜 "내 PC에서는 되는데..."를 방지한다.
> `.nvmrc`로 Node 버전을 고정하는 것과 비슷한 목적이다.

**주요 Gradle 명령어:**

```bash
./gradlew bootRun          # 개발 서버 실행 (NestJS의 npm run start:dev)
./gradlew build            # 프로젝트 빌드 + 테스트 (npm run build)
./gradlew test             # 테스트만 실행 (npm test)
./gradlew bootJar          # 실행 가능한 fat-JAR 파일 생성
./gradlew dependencies     # 의존성 트리 출력 (npm ls)
./gradlew clean            # 빌드 결과물 삭제 (rm -rf dist)
```

---

## 패키지 네이밍: `com.study.todo_spring`

Java의 패키지명은 도메인을 역순으로 사용하는 관례를 따른다.

```
com   .  study  .  todo_spring
──┬──    ──┬──     ────┬─────
  │         │           └─ 프로젝트명 (하이픈 → 언더스코어로 자동 변환)
  │         └─ 그룹/팀명
  └─ 최상위 도메인
```

> Spring Initializr에서 `todo-spring`으로 입력했으나,
> Java 패키지명에 하이픈(-)을 사용할 수 없으므로 `todo_spring`으로 자동 변환되었다.
> (HELP.md에도 이 사실이 명시되어 있다.)

---

## Spring Boot의 자동 설정(Auto Configuration) 동작 원리

`spring-boot-starter-webmvc` 의존성을 추가하면 Spring Boot는 다음을 **자동**으로 설정한다:

```
의존성 추가
    ↓
@EnableAutoConfiguration이 클래스패스 스캔
    ↓
spring-boot-autoconfigure 내 WebMvcAutoConfiguration 감지
    ↓
내장 Tomcat 서버 설정 (기본 포트 8080)
DispatcherServlet 등록 (모든 HTTP 요청의 진입점)
Jackson ObjectMapper 설정 (Java 객체 ↔ JSON 변환)
기본 에러 핸들러 설정 (/error 경로)
```

> **[NestJS 비교]**
> NestJS는 `NestFactory.create(AppModule)`에서 명시적으로 설정하지만,
> Spring Boot는 클래스패스에 있는 의존성을 감지해 **자동으로** 설정한다.
> 이것이 "Spring Boot = Spring + Auto Configuration"인 이유다.
> 직접 설정하고 싶다면 `application.properties` 또는 `@Configuration` 클래스로 오버라이드한다.

---

## 요청 처리 흐름 (Spring MVC)

```
HTTP 요청
    ↓
내장 Tomcat (포트 8080)
    ↓
DispatcherServlet (모든 요청의 관문 - Front Controller 패턴)
    ↓
HandlerMapping (어떤 Controller의 어떤 메서드가 처리할지 결정)
    ↓
@RestController 메서드 실행
    ↓
@Service 비즈니스 로직
    ↓
응답 객체 → Jackson이 JSON으로 직렬화
    ↓
HTTP 응답
```

> **[NestJS 비교]**
> NestJS는 `Middleware → Guard → Interceptor → Pipe → Controller → Interceptor(response)` 순서로 처리한다.
> Spring MVC는 `Filter → Interceptor → DispatcherServlet → Controller` 순서로 처리한다.
> 두 프레임워크 모두 요청 처리를 파이프라인 방식으로 구성한다는 공통점이 있다.

---

## 정리

| 파일 | 역할 | NestJS 대응 |
|------|------|------------|
| `build.gradle.kts` | 빌드 설정, 의존성 관리 | `package.json` |
| `settings.gradle.kts` | 프로젝트 이름, 멀티모듈 설정 | `nest-cli.json` (workspace) |
| `gradlew` / `gradlew.bat` | Gradle 버전 고정 실행 스크립트 | `npx` + `.nvmrc` |
| `TodoSpringApplication.java` | 앱 진입점, IoC 컨테이너 초기화 | `main.ts` |
| `application.properties` | 앱 설정 (DB, 서버, 로깅 등) | `.env` + `ConfigModule` |
| `static/` | 정적 파일 서빙 | `ServeStaticModule` |
| `templates/` | SSR 템플릿 | (해당 없음 - React 등 CSR 사용) |
| `TodoSpringApplicationTests.java` | 컨텍스트 로드 smoke test | `app.e2e-spec.ts` |
