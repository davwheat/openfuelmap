# Privacy Policy

David Wheatley made Open Fuel Map as an open source app. This service is supplied at no cost, and is intended for use as is.

This page tells you what data the app uses, where the data goes, and who can see it.

Open Fuel Map has no user accounts. It has no advertisements, no analytics tools, and no crash reporting tools.

## Data that stays on your device

The app keeps this data in its own storage on your device:

*   Your settings, such as your preferred fuel type and your hidden brands.
*   A local copy of forecourt data, brands, and fuel types, to make the app faster and to decrease network use.

I cannot read this data. To remove all of it, clear the app data or remove the app.

If you turn on Android cloud backup, Android can include this app data in your backup. Google controls that backup. Refer to the [Google Privacy Policy](https://policies.google.com/privacy).

## Location

The app asks for location permission, but the permission is optional. The app uses your location on your device, to move the map to your position and to calculate the distance to a forecourt.

**Your location coordinates are never sent to my servers.** The app does not record your location, and does not use location in the background.

But note that the app sends the map area that you look at to the API, to get the forecourts in that area. If you move the map to your position, that area shows approximately where you are. The area is much larger than a position, and the app sends it only when you look at the map.

You can refuse the permission, or remove it later in the Android settings. The remaining functions of the app continue to operate.

## Data sent to the Open Fuel Map API

The app gets fuel prices from `api.openfuelmap.co.uk`. This API is operated by me, and runs on Cloudflare Workers.

Each request includes:

*   The map area that you look at, or the forecourt that you select. This is an area, not your device position, but it can show approximately where you are. Refer to [Location](#location).
*   Your filters, such as the fuel type and the excluded brands.

As with all internet services, the network layer receives your IP address and your device user agent. Cloudflare processes these to supply and protect the service. Refer to the [Cloudflare Privacy Policy](https://www.cloudflare.com/privacypolicy/).

I do not keep a database of users, requests, or IP addresses. I do not try to identify persons or to make a profile of them.

## Map tiles

The map uses MapLibre, and gets map styles and tiles from `osm-assets.coveragetiles.com`. The map data comes from OpenStreetMap.

When the map loads tiles, that server receives your IP address and the map area that you look at. This server is not controlled by me.

## Google Play services

The app uses these Google Play services components:

*   Google Play services location, to get the device location on your device when you give permission.
*   Google Play services OSS licenses, to show the licences of the open source libraries.

If you install the app from Google Play, Google also collects data about the installation and the app usage. Refer to the [Google Privacy Policy](https://policies.google.com/privacy).

## Data sharing

I do not sell your data. I do not share your data with third parties, apart from the service operators listed above, which supply the necessary infrastructure.

## Security

No transmission on the internet and no electronic storage is fully secure. I cannot guarantee absolute security. But because the app collects no personal data on my systems, there is no user data store that can be lost.

## Links to other sites

The app can show links to other websites, for example the website of a fuel brand. I do not control these sites. Read the privacy policy of each site that you open.

## Children's privacy

This service does not target persons less than 13 years old. I do not knowingly collect personal data from children.

## Changes to this policy

I can change this privacy policy. I will put the new policy on this page. Examine this page from time to time.

This policy is effective as of 2026-08-02.

## Contact

If you have questions or suggestions about this privacy policy, send a message to android-developer-contact@davwheat.dev.
