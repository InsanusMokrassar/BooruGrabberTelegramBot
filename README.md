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
