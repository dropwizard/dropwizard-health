# dropwizard-health
[![Build Status](https://travis-ci.org/dropwizard/dropwizard-health.svg?branch=master)](https://travis-ci.org/dropwizard/dropwizard-health)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.dropwizard.modules/dropwizard-health/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.dropwizard.modules/dropwizard-health/)

Provides a health check implementation that performs ongoing monitoring of an application's dependencies and includes
an endpoint that can be called by a load balancer to determine if the application is healthy and thus able to receive
traffic.

##### Future improvements:
* Hooks for health status change events.
* Better support for Cloud-native live-ness and readiness checks.
* The ability to expose health check data to other modules (for instance, an Admin page may want to show historical health check results).
* More out-of-the-box generally useful health checks implementations, like file-system health checks.
 
## Usage
Add dependency on library.
```xml
<dependency>
  <groupId>io.dropwizard.modules</groupId>
  <artifactId>dropwizard-health</artifactId>
  <version>${dropwizard.version}</version>
</dependency>
```

### Health Checks
In your application's `Configuration` class, add a `HealthConfiguration` object:
```java
public class ExampleConfiguration extends Configuration {
    ...
     
    @Valid
    @NotNull
    @JsonProperty("health")
    private HealthConfiguration healthConfiguration = new HealthConfiguration();
    
    public HealthConfiguration getHealthConfiguration() {
        return healthConfiguration;
    }

    public void setHealthConfiguration(final HealthConfiguration healthConfiguration) {
        this.healthConfiguration = healthConfiguration;
    }
}
```

Add a `HealthCheckBundle` to the `Bootstrap` object in your `initialize` method:
```java
bootstrap.addBundle(new HealthCheckBundle<ExampleConfiguration>() {
    @Override
    protected HealthConfiguration getHealthConfiguration(final ExampleConfiguration configuration) {
        return configuration.getHealthConfiguration();
    }
});
```

Configure health checks for any dependencies your application has, such as any databases, caches, queues, etc.
Dropwizard modules that support connecting to a dependency will often register a health check automatically.

Define the following health check configurations in your `config.yml` file:
```yaml
health:
  delayedShutdownHandlerEnabled: true
  shutdownWaitPeriod: 30s
  healthChecks:
    - name: UserDatabase
      critical: true
    - name: UserNotificationTopic
      critical: false
      schedule:
        checkInterval: 2500ms
        downtimeInterval: 10s
        failureAttempts: 2
        successAttempts: 1
    - name: UserCache
      critical: false
```

## Configuration Reference
### Health Configuration
Name | Default | Description
---- | ------- | -----------
delayedShutdownHandlerEnabled | true | Flag indicating whether to delay shutdown to allow already processing requests to complete.
shutdownWaitPeriod | 15 seconds | Amount of time to delay shutdown by to allow already processing requests to complete. Only applicable if `delayedShutdownHandlerEnabled` is true.
healthCheckUrlPaths | \["/health-check"\] | URLs to expose the app's health check on.
healthChecks | [] | A list of configured health checks. See the [Health Check Configuration section](#health-check-configuration) for more details.

### Health Check Configuration
Name | Default | Description
---- | ------- | -----------
name | (none) | The name of this health check. This must be unique.
critical | false | Flag indicating whether this dependency is critical to determine the health of the application. If `true` and this dependency is unhealthy, the application will also be marked as unhealthy.
schedule | (none) | The schedule that this health check will be run on. See the [Schedule section](#schedule) for more details.

### Schedule
Name | Default | Description
---- | ------- | -----------
checkInterval | 5 seconds | The interval on which to perform a health check for this dependency while the dependency is in a healthy state.
downtimeInterval | 30 seconds | The interval on which to perform a health check for this dependency while the dependency is in an unhealthy state.
failureAttempts | 3 | The threshold of consecutive failed attempts needed to mark a dependency as unhealthy (from a healthy state).
successAttempts | 2 | The threshold of consecutive successful attempts needed to mark a dependency as healthy (from an unhealthy state).

## Example
Healthy
```bash
$ curl -v https://<hostname>:<port>/health-check
> GET /health-check HTTP/1.1
...
>
< HTTP/1.1 200 OK
< Content-Type: application/json
...
<
{"status": "healthy"}
```

Not Healthy
```bash
$ curl -v https://<hostname>:<port>/health-check
> GET /health-check HTTP/1.1
...
>
< HTTP/1.1 503 Service Unavailable
< Content-Type: application/json
...
<
{"status": "unhealthy"}
```

## HTTP and TCP Health Checks
Should your service have any dependencies that it needs to perform health checks against that expose either an HTTP or TCP health check interface,
you can use the `HttpHealthCheck` or `TcpHealthCheck` classes to do so easily. 

### Usage
You will need to register your health check(s) in your `Application` class `run()` method.

#### HTTP
```java
@Override
public void run(final AppConfiguration configuration, final Environment environment) {
    ...
    environment.healthChecks().register("http-service-dependency", new HttpHealthCheck("http://some-http-dependency.com:8080/health-check"));
}
```
#### TCP
```java
@Override
public void run(final AppConfiguration configuration, final Environment environment) {
    ...
    environment.healthChecks().register("tcp-service-dependency", new TcpHealthCheck("some-tcp-dependency.com", 443));
}
```

## Composite Health Checks
You might find you need a health check that is a composite of more than one other health check. For instance, consider the case where you have 
a database and a cache, and if only one of those two are unhealthy, your service can still fulfill a subset of functionality, and thus 
should not necessarily be marked down.

Let's say that you have a database health check registered under the name `UserDatabase` and a cache health check under the name `UserCache`.
Below, see an example of how you might create a health check that is a composite of these two checks. Note that there does not exist a 
`CompositeHealthCheck` class currently, but it might be a nice addition to this library.

#### Example Composite Health Check
```java
private void registerCompositeHealthCheck(final HealthCheckRegistry healthChecks) {
    final HealthCheck databaseCheck = healthChecks.getHealthCheck("UserDatabase");
    final HealthCheck cacheCheck = healthChecks.getHealthCheck("UserCache");

    final UserHealthCheck userHealthCheck = new UserHealthCheck(databaseCheck, cacheCheck);
    healthChecks.register("UserComposite", userHealthCheck);
}
```

#### Example Composite Health Check YAML
```yaml
health:
  delayedShutdownHandlerEnabled: true
  shutdownWaitPeriod: 30s
  healthChecks:
    - name: UserComposite
      critical: true
```
