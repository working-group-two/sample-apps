# Maven starter - Listen for events

This is a Java 11 sample app for listening to events from Working Group Two's event API.

See: https://docs.wgtwo.com/

We use ScribeJava for obtaining access tokens using the OAuth 2.0's client credentials flow.
Any OAuth 2.0 client will do, but we strongly recommend that you use a library instead of implementing the flow yourself. 

The sandbox environment does not require any credentials, so this may be disabled.
If you want to disable auth, replace `clientCredentialSource::accessToken` with `null` and delete all OAuth 2.0 setup.

## Build
```
./mvnw package
```

## Run
```
export WGTWO_CLIENT_ID={your client ID}
export WGTWO_CLIENT_SECRET={your client secret}
java -jar target/app-1.0-SNAPSHOT-jar-with-dependencies.jar
```
