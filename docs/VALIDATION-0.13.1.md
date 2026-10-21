# Pocket 0.13.1 validation

Main community app: `dev.pocketllama`, versionCode 34. Candidate validation and
exact signed-APK installation results are recorded here before delivery.

Completed locally: 55 agent checks; 27 web checks; 11 SearXNG tests including real
TLS and certificate rejection; four search distribution tests; 106 core, 14 notes
and 447 knowledge checks; RU/EN and browser/consent/source rendering checks.

Live production SearXNG with the executor egress configuration returned six dated
news sources for «Нефтекамск новости» and six for «science news». No provider
responses were mocked. Direct public-network CI and the native Android WebView
probe are pending. No claim is made that a physical phone has been tested.
