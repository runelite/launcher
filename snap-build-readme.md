#RuneLite snap build guide#
##Intro to Snappy##
Snap is a technology by Canonical, the company that develops Ubuntu, aiming to simplify Linux deployment. It is often referred to as the Snappy package manager.

It's main competition would be Flatpak, which has the same goal with other technologies. The benefits are roughly similar to the AppImage deployment but enhanced.

The snap build will force consistency on Mesa3D libraries, Java, ALSA libs, Pulseaudio lib's, and enforce Sandboxing on the client (support varying across distributions).

Importantly, the snap build will also update atomically and automatically for users.
If you have experience with Docker or other container tools, Snap is conceptually the same.

"snap" is the CLI tool for managing "snapd", the tool that manipulates .snap files, which are built using the tool "snapcraft".
Snapcraft.io is the repository for hosting snap files. It handles the actual distribution of the .snap files and generates signatures for those files.

##Building the RuneLite snap##

The first step to building a snap is to actually have snapd installed. Instructions can be found here:
https://snapcraft.io/docs/installing-snapd

Use snap to install the snapcraft tool:
`sudo snap install snapcraft --classic`

git clone the repo and cd into it:
`git clone https://github.com/runelite/launcher && cd launcher`

Usually, snapcraft can manage the maven build process itself. 
However given Adam's wishes of making his life easier given he alone builds all official releases, run maven yourself manually first. 
The build file will use the .jar in the target/ directory from the Maven build.
`mvn clean install`

Depending on your system specifications, the instructions will diverge.
If you are running your OS on bare metal, and the CPU supports hardware assisted virtualization - all you should need  is

`snapcraft`

If the tool moans about multipass, simply rerun it once or twice to fix a one off race condition on fresh installs.

If you are running your OS in a VM, your CPU / Hypervisor combination may or may not support nested hardware VM acceleration.

If it does, you can also just use 
`snapcraft`
In the event it doesn't, you will need to use container to build the snap in, see here

https://snapcraft.io/docs/build-on-lxd

The RuneLite snap makes use of a base snap, so follow the "Building with bases" instructions to do the setup for LXD. This should only be required once.

If using LXD, all calls to snapcraft should be made with
`snapcraft --use-lxd`

including for example,

`snapcraft clean --use-lxd`

Once the snap file is built, it can be installed with

`snap install runelite_release.snap --dangerous`

The dangerous flag is needed to allow installation without a signature. Only Adam can generate valid signatures as part of the process of submitting to the store.
However, the Snap should otherwise function identically.

If there are any errors with `snapcraft`, liberal use of `snapcraft clean` may become your best friend. You can selectively clean only one part of the snap, E.G, `snapcraft clean adoptopenjdk`, or the entire snap at once with `snapcraft clean`

##Tl;Dr##
1) Clone the repo

2) Run maven

3) Run snapcraft

