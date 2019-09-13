# graylog-logback

Для деплоя **релизных** артефактов сконфигурируйте секцию `build.gradle`:

```groovy
ext {
    ...
    nexusUrl = 'https://nexus.unitedtraders.team/nexus/repository/releases/'
    nexusUser = '<ваш логин>'
    nexusPassword = '<ваш пароль>'
}
```

Затем запустите

```shell script
./gradlew build uploadToNexus
```