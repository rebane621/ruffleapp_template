# RuffleAPK

This is a template project to build Android APKs wrapping Shockwave Flash files using the [Ruffle](https://ruffle.rs/) engine. The main focus of RuffleAPK is on flash games.

To make things easier to work with, there is a patcher config and script that will bootstrap the project template for you.

# Usage

Set up Android Studio with SDK 34 or later and import the project. You can then close Android Studio again and start working with the patcher.

You mainly have to change the value in ruffleapk.properties. **Don't be lazy about that - Unique package name are neccessary!**

After that you can run `python3 ruffleapk.py` and it should adjust the required file for a new APK.

If you're not satisfied with unsigned APKs, you can then hop back into Android Studio and do some fine tuning.

An arbitrary version of Ruffle is already dumped into the assets directory for easier use, but you might want to get a newer version from
either [ruffle.rs](https://ruffle.rs/) or their [GitHub repo](https://github.com/ruffle-rs/ruffle)

        To get started, click the green `Code` button above and pick `Download ZIP`