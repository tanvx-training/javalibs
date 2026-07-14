# Đóng góp vào javalibs

## Nguyên tắc bất di bất dịch

1. **`-core` không phụ thuộc Spring.** Cần Spring? Code đó thuộc tầng `-spring`.
2. Trong `-spring-boot-autoconfigure`, **mọi** thư viện thứ ba là `<optional>true</optional>`; **mọi** bean có `@ConditionalOnMissingBean`; tính năng cần hạ tầng là opt-in (`enabled` mặc định `false`).
3. `-spring-boot-starter` **không chứa code Java** — chỉ `pom.xml` + `README.md`.
4. **Không Lombok**; ưu tiên `record` + immutability; Javadoc tiếng Anh trên mọi public type; tài liệu tiếng Việt.
5. Không hardcode version cho dependency đã được `spring-boot-dependencies` hoặc root POM quản lý.
6. Không đổi hành vi mặc định trong MINOR release.
7. Bean auto-config đặt tên tiền tố `javalibs*` để tránh đụng tên với bean của service.

## Checklist: thêm module mới `javalibs-foo`

- [ ] Tạo aggregator `javalibs-foo/pom.xml` (packaging `pom`, parent `javalibs-root`, name `javalibs :: foo`)
- [ ] Submodule theo nhu cầu: `-core` (nếu có logic thuần) → `-spring` → `-spring-boot-autoconfigure` → `-spring-boot-starter`; package `io.javalibs.foo[.spring|.autoconfigure]`
- [ ] Property namespace `javalibs.foo.*` qua class `*Properties` + `spring-boot-configuration-processor` (optional) để sinh metadata
- [ ] Đăng ký auto-config trong `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] **Thêm module vào `<modules>` của root `pom.xml`**
- [ ] **Thêm mọi jar artifact vào BOM `javalibs-dependencies/pom.xml`** (build không tự nhắc — quy tắc review!)
- [ ] Test: unit test cho core/spring + `ApplicationContextRunner`/`WebApplicationContextRunner` cho autoconfigure (đủ 4 hướng: mặc định, disabled, thiếu điều kiện, user bean back-off)
- [ ] `README.md` tiếng Việt cho aggregator và starter
- [ ] Tài liệu `docs/modules/foo.md` theo template + thêm dòng vào `docs/index.md` và `docs/configuration-reference.md`
- [ ] `mvn clean install` xanh từ root

## Checklist review PR

- [ ] Đúng phân tầng (không import Spring trong core, không code trong starter)
- [ ] Dependency mới: đúng scope/optional? Version có bị hardcode không?
- [ ] Bean mới có `@ConditionalOnMissingBean`? Có cờ tắt không?
- [ ] Đổi hành vi mặc định? → cần MAJOR hoặc làm opt-in
- [ ] Exception message có "hành động được" không (nói rõ property/bước cần làm)?
- [ ] Test autoconfigure đủ các hướng điều kiện
- [ ] BOM + docs được cập nhật đồng bộ
- [ ] Input từ bên ngoài được validate (tên field/table, header, kích thước)?

## Template test autoconfigure chuẩn

```java
class FooAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FooAutoConfiguration.class));

    @Test
    void activeByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(FooService.class));
    }

    @Test
    void disabledByFlag() {
        runner.withPropertyValues("javalibs.foo.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FooService.class));
    }

    @Test
    void backsOffForUserBean() {
        runner.withUserConfiguration(UserFooConfig.class)
                .run(context -> assertThat(context.getBean(FooService.class))
                        .isInstanceOf(UserFooService.class));
    }

    @Test
    void inactiveWithoutRequiredClass() {
        runner.withClassLoader(new FilteredClassLoader(RequiredLib.class))
                .run(context -> assertThat(context).doesNotHaveBean(FooService.class));
    }
}
```

## Cấu trúc tài liệu

```
docs/
├── index.md                    # mục lục + ma trận tính năng
├── getting-started.md
├── architecture.md
├── configuration-reference.md  # cập nhật khi thêm/đổi property
├── cookbook.md                 # thêm recipe khi có pattern mới
├── operations.md
├── contributing.md             # file này
└── modules/<name>.md           # tham chiếu chi tiết từng module
```
