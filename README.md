# BooruGrabberTelegramBot

Bot for booru-boards grabbing. It uses [imageboard-api](https://github.com/Kodehawa/imageboard-api) for requests to boards and support next boards as well as `imageboard-api`:

* Rule34
* e621
* Konachan
* Yande.re
* Danbooru
* Safebooru
* Gelbooru
* e926

Sample bot presented here: [@booru_grabber_bot](https://t.me/booru_grabber_bot)

## Fast how to start

1. Create a `config.json`. Minimal required config:

```json
{
  "token": "your bot token",
  "database": {
    "url": "jdbc:postgresql://booru_grabber_postgres:5432/test",
    "username": "test",
    "password": "test"
  }
}
```

All available properties:

```json
{
  "token": "your bot token",
  "database": {
    "url": "jdbc:postgresql://booru_grabber_postgres:5432/test",
    "driver": "org.postgresql.Driver",
    "username": "test",
    "password": "test",
    "reconnectOptions": {
      "attempts": 3,
      "delay": 1000
    }
  },
  "client": {
    "connectionTimeoutMillis": null,
    "requestTimeoutMillis": null,
    "responseTimeoutMillis": null,
    "proxy": {
      "hostname": "proxy.example.com",
      "type": "socks",
      "port": 1080,
      "username": null,
      "password": null
    }
  }
}
```

Property reference:

**`token`** *(required)* — Telegram bot token from [@BotFather](https://t.me/BotFather).

**`database`** *(required)*:

- `url` — JDBC connection URL (default: `jdbc:pgsql://localhost:12346/test`)
- `driver` — JDBC driver class (default: `org.postgresql.Driver`)
- `username` — database user (default: empty)
- `password` — database password (default: empty)
- `reconnectOptions.attempts` — how many times to retry connecting on startup (default: `3`)
- `reconnectOptions.delay` — delay in milliseconds between retries (default: `1000`)

**`client`** *(optional)* — HTTP client settings for Telegram API requests:

- `connectionTimeoutMillis` — connection timeout in ms
- `requestTimeoutMillis` — write/request timeout in ms
- `responseTimeoutMillis` — read/response timeout in ms
- `proxy.hostname` *(required if proxy set)* — proxy host
- `proxy.type` — `socks` (default) or `http`
- `proxy.port` — proxy port (default: `1080` for socks, `3128` for http)
- `proxy.username` — proxy username (optional; for socks, password is required when username is set)
- `proxy.password` — proxy password (optional)

2. In `docker-compose.yml`, uncomment the `booru_grabber_bot` service and set the path to your config file:

```yaml
services:
  booru_grabber_postgres:
    image: postgres
    container_name: "booru_grabber_postgres"
    environment:
      POSTGRES_USER: "test"
      POSTGRES_PASSWORD: "test"
      POSTGRES_DB: "test"
  booru_grabber_bot:
    image: insanusmokrassar/booru_grabber_bot
    container_name: "booru_grabber_bot"
    volumes:
      - "/absolute/path/to/config.json:/booru_grabber_bot/config.json:ro"
```

3. Start the services:

```bash
docker-compose up -d
```

## Available commands

Bot have two helping commands: `/start` and `/help`. These commands will return help for bot `/request`/`/enable` commands:

```bash
Usage: enable [OPTIONS] QUERY...

Options:
  -n INT                           Amount of pictures to grab each trigger
                                   time
  -k, --krontab TEXT...            Krontab in format * * * * *. See
                                   https://bookstack.inmo.dev/books/krontab/page/string-format
  -b, --board VALUE                Board type. Possible values: r34, e621,
                                   konachan, yandere, danbooru, safebooru,
                                   gelbooru, e926
  -g, --gallery                    Effective only when count passed > 1. Will
                                   send chosen images as gallery instead of
                                   separated images
  -r, --rating [safe|general|questionable|sensitive|explicit]
  -a, --attach_urls
  -h, --help                       Show this message and exit

Arguments:
  QUERY  Your query to booru. Use syntax "-- -sometag" to add excluding of
         some tag in query
```

As said previously, there are `/request` and `/enable`

### Request parameters

I will omit obvious parameters like `-n`.

* `-b`/`--board` - select which board to use
* `-n` (optional, default 1, max 10)
* `-k`/`--krontab` (optional) - use [krontab](https://bookstack.inmo.dev/books/krontab/page/string-format)-string for setting up request from time to time. Unfortunatelly, **currently supported only main five settings: seconds, minutes, hours, days and months**
* `-g`/`--gallery` (optional) - flag indicates that in case you passed `-n` more than 1 messages should be sent as media group
* `-r`/`--rating` (optional) - rating of images in requests
* `-a`/`--attach_urls` (optional) - will force bot to attach url of image to the sent images

Besides, after all parameters you should add query for the board like `rem_(re:zero)`:

```
/request -n 3 -a -b safebooru -g rem_(re:zero)
```

If you want to use negative query parameters, you must escape them with `--` before parameter:

`-- -1girl` (exclude posts with `1girl` tag)

Bot remember which posts it already has sent to the chat. So, there should not be repetitions in the sent images

### Samples

All the samples will use query `rem_(re:zero) -- -1girl` (in fact it is query `rem_(re:zero) -1girl`) and `-r safe` just to make request suitable for work.

---

Sample from above to request 3 photos in gallery mode from safebooru with urls attaching:

```
/request -n 3 -a -b safebooru -g rem_(re:zero) -- -1girl
```

---

Same sample as above, but with requests each day at 22:00 UTC

```
/enable -k 0 0 22 * * -n 3 -a -b safebooru -g rem_(re:zero) -- -1girl
```

---

Same sample as above, but with requests each day at 22:00 UTC

```
/enable -k 0 0 22 * * -n 3 -a -b safebooru -g rem_(re:zero) -- -1girl
```

Will enable autorequests of images using [krontab](https://bookstack.inmo.dev/books/krontab/page/string-format) and other parameters.

Sample:

```bash
/enable -n 3 -k 0 0 18 * * -b safebooru -g 1girl gradient
```

You may use the same syntax with just replacing of `/enable` by `/request`
