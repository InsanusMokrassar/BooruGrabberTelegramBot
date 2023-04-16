FROM adoptopenjdk/openjdk11

USER 1000

ENTRYPOINT ["/booru_grabber_bot/bin/booru_grabber_bot", "/booru_grabber_bot/config.json"]

ADD ./build/distributions/booru_grabber_bot.tar /
