---
name: cmtool
description: Interact with the Zoho CMTool REST API for users and products — querying users by username/email, looking up products by id/name/repository_url/download_url, and creating/updating/deleting resources. Use when the user mentions CMTool, CMTools, PRIVATE-TOKEN, cmtools.csez, product lookup, build URL, repository URL, or needs to fetch product/user metadata from the Zoho build infrastructure.
---

# CMTool API

Zoho internal REST API at `https://cmtools.csez.zohocorpin.com/api/v1/`. Only `zohocorp.com` users may call it. Responses are JSON.

## Authentication

Every request requires the header:

```
PRIVATE-TOKEN: <auth_token>
```

In the ZIDE IntelliJ plugin the token is stored via `ZideSettingsState.cmToolAuthToken` (Settings > Tools > Zide > CMTool). Always ensure the token is set before making API calls.

## Endpoints

### Users — `/api/v1/users`

| Method | Description |
|--------|-------------|
| GET    | Retrieve users |
| POST   | Create user |
| PUT    | Update user |
| DELETE | Delete user |

**Query parameters** (at least one required):

| Parameter | Type   |
|-----------|--------|
| username  | String |
| email     | String |

**Example request:**
```
GET https://cmtools.csez.zohocorpin.com/api/v1/users?username=john
```

**Example response:**
```json
{
  "users": [
    {
      "id": 42,
      "username": "john",
      "email": "john@zohocorp.com",
      "is_active": true,
      "is_admin": false,
      "created_at": null,
      "updated_at": "2019-06-27T23:34:19.000+05:30",
      "last_visited_product_id": 38
    }
  ],
  "meta": { "total_pages": 1, "per_page": 20, "total_count": 1 }
}
```

### Products — `/api/v1/products`

| Method | Description |
|--------|-------------|
| GET    | Retrieve products |
| POST   | Create product |
| PUT    | Update product |
| DELETE | Delete product |

**Query parameters:**

| Parameter      | Type   | Notes |
|----------------|--------|-------|
| id             | String | required for single lookup |
| name           | String | optional search |
| download_url   | String | optional search |
| repository_url | String | optional search |
| team_mail_id   | String | optional search |

**Single product by ID:**
```
GET https://cmtools.csez.zohocorpin.com/api/v1/products/PRODUCT_ID
```

**Search:**
```
GET https://cmtools.csez.zohocorpin.com/api/v1/products?name=XXX&repository_url=http://build/xxx/xxxxx
```

**List user's products:**
```
GET https://cmtools.csez.zohocorpin.com/api/v1/products?personal=true&include_role_acccess=true
```

## Product Response Schema

```json
{
  "products": {
    "id": 7243,
    "name": "PRODUCT_NAME",
    "known_as": "PRODUCT_DISPLAY_NAME",
    "group_id": 16,
    "repository_type_id": 5,
    "zrepo_id": "26000119703269",
    "download_url": "https://build.zohocorp.com/group/product",
    "us_download_url": null,
    "module_name": "product_module",
    "build_owner_id": null,
    "is_active": true,
    "is_released": false,
    "is_webhost_enabled": true,
    "is_ci_enabled": false,
    "ci_type": null,
    "team_email_id": "team@zohocorp.com",
    "srclabel": "HEAD",
    "repository_url": "https://repository.zohocorpcloud.in/zohocorp/group/product.git",
    "service_name": "product_service",
    "created_at": "2024-10-23T16:15:49.000+05:30",
    "updated_at": "2025-09-27T12:20:14.000+05:30"
  }
}
```

### Key Product Fields

| Field | Description |
|-------|-------------|
| `id` | Unique product identifier |
| `name` | Product name (uppercase convention) |
| `known_as` | Display alias |
| `download_url` | Build download base URL |
| `repository_url` | Source repository URL |
| `module_name` | Module identifier |
| `service_name` | Service identifier |
| `team_email_id` | Team contact email |
| `is_active` | Whether product is active |
| `is_released` | Whether product has been released |
| `is_webhost_enabled` | WebHost deployment flag |
| `is_ci_enabled` | CI integration flag |
| `ci_type` | CI system type (e.g. "jenkins") |
| `srclabel` | Source label (e.g. "HEAD") |
| `repository_type_id` | Repository backend type (5=git, 6=svn) |
| `zrepo_id` | Zoho Repository internal ID |

## Usage in ZIDE Plugin

1. `ZideNewProjectAction` and `ZideModuleBuilder` call `ensureCmToolToken()` before proceeding
2. Token stored in `ZideSettingsState.cmToolAuthToken` (persisted in `dzide-settings.xml`)
3. `ZideProjectWizardDialog` uses product data to populate the **Service** dropdown
4. Fields `repository_url`, `download_url`, and `service_name` drive git clone and build download

### Making HTTP Calls in Kotlin (IntelliJ Plugin)

```kotlin
val url = URL("https://cmtools.csez.zohocorpin.com/api/v1/products?name=$productName")
val conn = url.openConnection() as HttpURLConnection
conn.requestMethod = "GET"
conn.setRequestProperty("PRIVATE-TOKEN", settings.cmToolAuthToken)
conn.setRequestProperty("Content-Type", "application/json")

val response = conn.inputStream.bufferedReader().readText()
val json = JsonParser.parseString(response).asJsonObject
```

## Pagination

List responses include a `meta` object:

```json
{
  "meta": {
    "total_pages": 5,
    "per_page": 20,
    "total_count": 97
  }
}
```

Use `page` query parameter to paginate: `?name=XXX&page=2`.

## Error Handling

- **401 Unauthorized** — invalid or missing `PRIVATE-TOKEN`
- **404 Not Found** — product/user ID does not exist
- **422 Unprocessable Entity** — missing required parameters

Always validate `PRIVATE-TOKEN` is non-empty before making requests. In the plugin, prompt via `Messages.showInputDialog` if the token is missing.

## User Response Schema

```json
{
  "users": [
    {
      "id": 42,
      "username": "john",
      "email": "john@zohocorp.com",
      "is_active": true,
      "is_admin": true,
      "created_at": null,
      "updated_at": "2019-06-27T23:34:19.000+05:30",
      "last_visited_product_id": 38
    }
  ],
  "meta": {
    "total_pages": 1,
    "per_page": 20,
    "total_count": 1
  }
}
```

## Additional Resources

- CMTool Auth Token setup: [Zoho Learn FAQ](https://learn.zoho.in/portal/zohocorp/manual/zide-faqs/article/cmtools-auth-token-required)
