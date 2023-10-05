FROM adoptopenjdk/openjdk11

ADD ./build/distributions/booru_grabber_bot.tar /
RUN chown -R 1000:1000 "/booru_grabber_bot"

USER 1000

ENTRYPOINT ["/booru_grabber_bot/bin/booru_grabber_bot", "/booru_grabber_bot/config.json"]
