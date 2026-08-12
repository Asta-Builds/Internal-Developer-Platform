server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: {{repoName}}
{{postgresConfig}}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus