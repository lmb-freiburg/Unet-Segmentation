# Unet-Segmentation

This is the source of the U-Net Segmentation plugin for Fiji.
You can obtain the latest stable version directly from the Fiji updater via update site "U-Net Segmentation".

For more details check our [project page](https://lmb.informatik.uni-freiburg.de/resources/opensource/unet).
Binary releases are available on our [Fiji update site](https://sites.imagej.net/Falk/plugins/)

The plugin requires connection to a Linux workstation (can be the local computer) running a special variant of caffe (caffe_unet).

## U-Net Segmentation backend caffe_unet (binaries, source, docker)

Obtain the U-Net segmentation backend (caffe_unet) binaries and corresponding caffe source patch from the [project page](https://lmb.informatik.uni-freiburg.de/resources/opensource/unet).
Please also check our [caffe-unet-docker](https://github.com/lmb-freiburg/caffe-unet-docker) repository.

## Build from source

We recommend to use Linux for building from source, in theory building on Windows should work, but it is not tested.

### Prerequisites:
- cmake
- Java SE 8
- Fiji (ij, jsch, jhdf5, protobuf-java) https://fiji.sc/
- google protobuf compiler (protoc) https://github.com/protocolbuffers/protobuf/releases

ij.jar, jsch.jar and jhdf5.jar should be already included in an of-the-shelf Fiji installation. You can obtain protobuf-java from the U-Net update site.

### General build instructions
Clone this repository, create a separate build directory, and run cmake using the cloned directory as source folder and the build directory as destination folder. Choose your Fiji plugins folder as install prefix.

### Example:

- Fiji is installed in /home/user/Fiji.app
- You installed the protobuf compiler using your package management system (e.g. on debian-based systems using apt-get install protobuf-compiler)
- You cloned this repository to /home/user/Unet-Segmentation

Then the following block should build and install the Unet-Segmentation plugin into Fiji. The API documentation can be found in /home/user/Unet-Segmentation/build/javadoc/doc/.

```
mkdir -p /home/user/Unet-Segmentation/build
cd /home/user/Unet-Segmentation/build
cmake -DCMAKE_INSTALL_PREFIX=/home/user/Fiji.app/plugins -DFIJI_BIN=/home/user/Fiji.app/ImageJ-linux64  ..
make install
```
