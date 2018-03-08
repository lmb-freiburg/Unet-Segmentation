cmake_minimum_required (VERSION 2.8)

find_package(Java REQUIRED)
include(UseJava)

project(Unet_Segmentation Java)

set(CMAKE_JAVA_COMPILE_FLAGS "-Xlint:unchecked" "-deprecation")

find_program(FIJI_BIN ImageJ-linux64 DOC "The Fiji binary")
find_program(PROTOBUF_COMPILER protoc DOC "Google Protobuf compiler")
get_filename_component(FIJI_DIR ${FIJI_BIN} DIRECTORY)

# Get current version of ij.jar
execute_process(COMMAND find ${FIJI_DIR}/jars -name "ij-*.jar" OUTPUT_VARIABLE IJ_JAR_ABSPATH)
get_filename_component(IJ_JAR_FILENAME ${IJ_JAR_ABSPATH} NAME)
string(REGEX REPLACE "[.]jar.*" "" IJ_JAR_BASENAME ${IJ_JAR_FILENAME})
if ("${IJ_JAR_BASENAME}" STREQUAL "")
  set(IJ_JAR_BASENAME "ij.jar")
endif ("${IJ_JAR_BASENAME}" STREQUAL "")
find_jar(IJ_JAR "${IJ_JAR_BASENAME}" PATHS ${FIJI_DIR}/jars DOC "Path to the ImageJ library ij-<version>.jar")

# Get current version of jhdf5.jar
execute_process(COMMAND find ${FIJI_DIR}/jars -name "jhdf5-*.jar" OUTPUT_VARIABLE JHDF5_JAR_ABSPATH)
get_filename_component(JHDF5_JAR_FILENAME ${JHDF5_JAR_ABSPATH} NAME)
string(REGEX REPLACE "[.]jar.*" "" JHDF5_JAR_BASENAME ${JHDF5_JAR_FILENAME})
find_jar(JHDF5_JAR "${JHDF5_JAR_BASENAME}" PATHS ${FIJI_DIR}/jars DOC "Path to the Java Hdf5 library jhdf5-<version>.jar")

# Get current version of jsch5.jar
execute_process(COMMAND find ${FIJI_DIR}/jars -name "jsch-*.jar" OUTPUT_VARIABLE JSCH_JAR_ABSPATH)
get_filename_component(JSCH_JAR_FILENAME ${JSCH_JAR_ABSPATH} NAME)
string(REGEX REPLACE "[.]jar.*" "" JSCH_JAR_BASENAME ${JSCH_JAR_FILENAME})
find_jar(JSCH_JAR "${JSCH_JAR_BASENAME}" PATHS ${FIJI_DIR}/jars DOC "Path to the Java Secure Shell library jsch-<version>.jar")

set(CMAKE_JAVA_INCLUDE_PATH ${IJ_JAR}:${JHDF5_JAR}:${JSCH_JAR}:${PROJECT_SOURCE_DIR}/protobuf-java-2.6.1.jar)

add_jar(Unet_Segmentation
  src/de/unifreiburg/unet/JobManager.java
  src/de/unifreiburg/unet/JobTableModel.java
  src/de/unifreiburg/unet/HostConfigurationPanel.java
  src/de/unifreiburg/unet/Job.java
  src/de/unifreiburg/unet/SegmentationJob.java
  src/de/unifreiburg/unet/DetectionJob.java
  src/de/unifreiburg/unet/FinetuneJob.java
  src/de/unifreiburg/unet/Tools.java
  src/de/unifreiburg/unet/MyUserInfo.java
  src/de/unifreiburg/unet/ProgressMonitor.java
  src/de/unifreiburg/unet/ModelDefinition.java
  src/de/unifreiburg/unet/ProcessResult.java
  src/de/unifreiburg/unet/ImagePlusListView.java
  src/de/unifreiburg/unet/NotImplementedException.java
  src/de/unifreiburg/unet/DistanceTransform.java
  src/de/unifreiburg/unet/ConnectedComponentLabeling.java
  src/de/unifreiburg/unet/Pair.java
  src/de/unifreiburg/unet/BlobException.java
  src/de/unifreiburg/unet/Blob.java
  caffe/Caffe.java
  caffe/caffe.proto COPYING.txt plugins.config
  resources/README.html INCLUDE_JARS protobuf-java-2.6.1.jar)

get_target_property(UNET_SEGMENTATION_JAR_ABSPATH Unet_Segmentation JAR_FILE)

add_custom_command(TARGET Unet_Segmentation
  POST_BUILD
  # workaround to add sources
  COMMAND jar uMf ${UNET_SEGMENTATION_JAR_ABSPATH} -C ${PROJECT_SOURCE_DIR} src
  )

add_custom_command(OUTPUT ${PROJECT_SOURCE_DIR}/caffe/Caffe.java
  COMMAND ${PROTOBUF_COMPILER} -I${PROJECT_SOURCE_DIR}/src --java_out=${PROJECT_SOURCE_DIR} ${PROJECT_SOURCE_DIR}/caffe/caffe.proto
  )

install_jar(Unet_Segmentation ${CMAKE_INSTALL_PREFIX})
