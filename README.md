# opensim-jlib

A library used by other OpenSim AI Stack components. 

**For Issues And Discussions see main project [opensim-ai-docker](https://github.com/opensim-stack/opensim-ai-docker)**

*This is part of the [opensim-stack](https://opensim-stack.github.io/) and is intended to be used in conjunction with other parts of the stack. See [Docs](https://opensim-stack.github.io/docs/index.html) for full details.*

## RemoteAdmin XML-RPC client

`OpensimRemoteAdminClient` provides a JDK-only XML-RPC client for OpenSimulator `admin_*` methods.

### Basic usage

```java
var client = new OpensimRemoteAdminClient("http://terra.southpark.lan:9000/", "changeme");

// Calls admin_get_agents with required fields
var regions = client.getAgents("Sandbox", null);

// Optional include_children overload
var regionsWithChildren = client.getAgents("Sandbox", null, true);

for (var region : regions) {
    for (var agent : region.agents()) {
        System.out.println(region.name() + ": " + agent.name());
    }
}
```

### Builder-based request example

```java
var request = OpensimRemoteAdminClient
        .teleportRequestBuilder("00000000-0000-0000-0000-000000000000", "Sandbox")
        .position(128.0, 128.0, 25.0)
        .lookAt(0.0, 1.0, 0.0)
        .noFly(true)
        .parameter("draw_distance", 128)
        .build();

var response = client.teleportAgent(request);
```

### Generic call for methods not wrapped yet

```java
var response = client.callAdminForStruct("create_region", Map.of(
        "region_name", "My Region",
        "region_id", "11111111-2222-3333-4444-555555555555"
));
```

### User account management wrappers

`OpensimRemoteAdminClient` includes typed wrappers for common account operations:

- `createUser(...)` -> returns `avatar_uuid` (`String`)
- `existsUser(...)` -> returns `lastlogin` as `Instant`
- `updateUserPassword(...)`, `updateUserStart(...)`, `updateUser(...)` -> return `avatar_uuid` (`String`)
- `authenticateUser(...)` -> takes `char[]` password, hashes MD5 internally, returns `token` (`String`)

```java
var avatarUuid = client.createUser("Test", "User", "Passw0rd!", 1000, 1000, "test@example.com");
var lastLogin = client.existsUser("Test", "User");
var updated1 = client.updateUserPassword("Test", "User", "Passw0rd2!");
var updated2 = client.updateUserStart("Test", "User", 1000, 1000);
var updated3 = client.updateUser("Test", "User", "Passw0rd3!", 1000, 1000);
var token = client.authenticateUser("Test", "User", "Passw0rd3!".toCharArray(), 30);
```

### Region and control wrappers

`OpensimRemoteAdminClient` now also includes:

- `broadcast(String message)`
- `closeRegionByName(String regionName)` / `closeRegionById(String regionId)`
- `createRegion(CreateRegionRequest request)` -> `CreateRegionResponse`
- `deleteRegion(String regionName)`
- `modifyRegion(boolean enableVoice, boolean isPublic)`
- `modifyRegionByName(String regionName, boolean enableVoice, boolean isPublic)`
- `modifyRegionById(String regionId, boolean enableVoice, boolean isPublic)`
- `regionQueryByName(String regionName)` / `regionQueryById(String regionId)` -> `health` (`int`)
- `restart(String regionId)` -> `rebooting` (`boolean`), throws `RequestNotAcceptedException` if `accepted=false`
- `shutdown()` and `shutdown(ShutdownRequest request)` (same `accepted=false` handling)
- `estateReload()`
- `consoleCommand(String command)`
- `dialog()`
- `resetLand()`
- `refreshSearch()`
- `refreshMap()`
- `getOpenSimVersion()` -> `version` (`String`)
- `getAgentCount()` -> `count` (`int`)

```java
var createReq = OpensimRemoteAdminClient
        .createRegionBuilder("My Region", "0.0.0.0", 9001, "example.org", 1001, 1001, "Main Estate")
        .persist(true)
        .regionFile("Regions/MyRegion.ini")
        .isPublic(true)
        .enableVoice(true)
        .build();

var created = client.createRegion(createReq);
var health = client.regionQueryByName(created.regionName());
```

```java
var shutdownReq = OpensimRemoteAdminClient.shutdownRequestBuilder()
        .delayed(5000)
        .noticeTypeDialog()
        .build();
client.shutdown(shutdownReq);
```

### Region access management wrappers

`OpensimRemoteAdminClient` includes ACL wrappers:

- `aclListByName(String regionName)` / `aclListById(String regionId)` -> `List<String>`
- `aclClearByName(String regionName)` / `aclClearById(String regionId)`
- `aclAddByName(String regionName, Collection<String> users)` / `aclAddByName(String regionName, String... users)`
- `aclAddById(String regionId, Collection<String> users)` / `aclAddById(String regionId, String... users)`
- `aclRemoveByName(String regionName, Collection<String> users)` / `aclRemoveByName(String regionName, String... users)` -> `removed` count (`int`)
- `aclRemoveById(String regionId, Collection<String> users)` / `aclRemoveById(String regionId, String... users)` -> `removed` count (`int`)

```java
client.aclAddByName("Some Region", "John Doe", "Jane Doe");
var users = client.aclListByName("Some Region");
var removed = client.aclRemoveByName("Some Region", "John Doe");
client.aclClearByName("Some Region");
```

### Region file management wrappers

`OpensimRemoteAdminClient` includes wrappers for region file operations:

- `loadHeightmap(LoadHeightmapRequest request)`
- `loadOarByName(String regionName, String filename)` / `loadOarById(String regionId, String filename)`
- `loadXmlByName(...)` / `loadXmlById(...)` (default `xml_version=1`, optional explicit version)
- `saveHeightmapByName(String regionName, String filename)` / `saveHeightmapById(String regionId, String filename)`
- `saveOar(SaveOarRequest request)`
- `saveXml(SaveXmlRequest request)`

```java
var hmReq = OpensimRemoteAdminClient
        .loadHeightmapByName("Some Region", "/tmp/region.r32")
        .merge(true)
        .skipAssets(false)
        .build();
client.loadHeightmap(hmReq);

client.saveOar(OpensimRemoteAdminClient
        .saveOarByName("Some Region", "/tmp/region.oar")
        .perm("CT")
        .build());
```

## Integration tests (disabled by default)

The `remoteadmin-it` Maven profile runs live integration tests (`*IT.java`) and excludes dangerous tests (`*DangerousIT.java`). This includes user account tests, ACL tests, plus create/modify/query/delete region flow tests.

```bash
mvn -Premoteadmin-it \
  -Dopensim.remoteadmin.endpoint=http://terra.southpark.lan:9000/ \
  -Dopensim.remoteadmin.password=changeme \
  test
```

Dangerous calls (broadcast/restart/shutdown) are isolated in `remoteadmin-dangerous-it`:

```bash
mvn -Premoteadmin-dangerous-it \
  -Dopensim.remoteadmin.endpoint=http://terra.southpark.lan:9000/ \
  -Dopensim.remoteadmin.password=changeme \
  -Dopensim.remoteadmin.allowDangerous=true \
  -Dopensim.remoteadmin.dangerous.regionId=<region-uuid> \
  test
```