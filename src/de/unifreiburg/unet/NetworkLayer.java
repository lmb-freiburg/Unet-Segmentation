/**************************************************************************
 *
 * Copyright (C) 2018 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 **************************************************************************/

package de.unifreiburg.unet;

import caffe.Caffe;

import java.util.Vector;

/**
 * NetworkLayer is the abstract base class of all unet layer implementations.
 * It provides functionality to compute the required memory of the
 * corresponding caffe Layer.
 *
 * @author Thorsten Falk
 * @version 1.0
 * @since 1.0
 */
public abstract class NetworkLayer {

  /**
   * Creates a new <code>NetworkLayer</code> object.
   *
   * @param layerParam the parameters used to setup the layer in compiled
   *   protocol buffer format
   * @param net the parent <code>Net</code> object
   * @param in the input blobs for this layer
   *
   * @see caffe.Caffe.LayerParameter
   */
  public NetworkLayer(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in) {
    _layerParam = layerParam;
    _net = net;
    _in = in;
    _out = new CaffeBlob[layerParam.getTopCount()];
  }

  /**
   * Factory method to create a new <code>NetworkLayer</code> object.
   *
   * @param layerParam the parameters used to setup the layer in compiled
   *   protocol buffer format
   * @param net the parent <code>Net</code> object
   * @param in the input blobs for this layer
   * @return a new concrete <code>NetworkLayer</code> corresponding to the
   *   caffe Layer specified by the layer parameters
   * @throws NotImplementedException if the layer type specified by the
   *   protobuf object has no corresponding unet implementation
   * @throws BlobException if the output blobs cannot be generated
   *
   * @see caffe.Caffe.LayerParameter
   */
  public static NetworkLayer createFromProto(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in)
      throws NotImplementedException, BlobException {

    if (layerParam.getType().equals("CreateDeformation"))
        return new CreateDeformationLayer(layerParam, net, in);
    if (layerParam.getType().equals("ApplyDeformation"))
        return new ApplyDeformationLayer(layerParam, net, in);
    if (layerParam.getType().equals("ValueAugmentation"))
        return new ValueAugmentationLayer(layerParam, net, in);
    if (layerParam.getType().equals("ValueTransformation"))
        return new ValueTransformationLayer(layerParam, net, in);
    if (layerParam.getType().equals("Convolution"))
        return new ConvolutionLayer(layerParam, net, in);
    else if (layerParam.getType().equals("ReLU"))
        return new ReLULayer(layerParam, net, in);
    else if (layerParam.getType().equals("Pooling"))
        return new PoolingLayer(layerParam, net, in);
    else if (layerParam.getType().equals("Deconvolution"))
        return new UpConvolutionLayer(layerParam, net, in);
    else if (layerParam.getType().equals("Concat"))
        return new ConcatAndCropLayer(layerParam, net, in);
    else if (layerParam.getType().equals("Dropout"))
        return new DropoutLayer(layerParam, net, in);
    else if (layerParam.getType().equals("SoftmaxWithLoss"))
        return new SoftmaxWithLossLayer(layerParam, net, in);
    else throw new NotImplementedException(
        "Layer type " + layerParam.getType() + " not implemented");
  }

  /**
   * Get the layer type as string.
   *
   * @return the type of this layer
   */
  public final String layerTypeString() {
    return _layerParam.getType();
  }

  /**
   * Get the layer name.
   *
   * @return the name of this layer
   */
  public final String name() {
    return _layerParam.getName();
  }

  /**
   * Get the input blobs for this layer.
   * <p>
   *   Treat the returned blob array as read-only. Changing any blob
   *   parameters will result in undefined behaviour.
   *
   * @return a reference to the input blob array of this layer
   */
  public final CaffeBlob[] inputBlobs() {
    return _in;
  }

  /**
   * Get the output blobs for this layer.
   * <p>
   *   Treat the returned blob array as read-only. Changing any blob
   *   parameters will result in undefined behaviour.
   *
   * @return a reference to the output blob array of this layer
   */
  public final CaffeBlob[] outputBlobs() {
    return _out;
  }

  /**
   * Get the memory consumption for learnable parameters of this layer.
   *
   * @return the memory required to store learnable parameters in bytes
   */
  public long memoryParameters() {
    return 0;
  }

  /**
   * Get the memory consumption for internal data structures.
   *
   * @return the memory required for internal use in bytes
   */
  public long memoryOther() {
    return 0;
  }

  /**
   * Get the memory overhead for cuDNN aware operations. It will either
   * output the memory required for additional cuDNN workspaces or if
   * <code>cuDNN == false</code> alternative data structures.
   *
   * @param cuDNN returns required memory for workspaces if <code>true</code>,
   *   or memory required for alternative data structures otherwise
   * @return the memory required for cuDNN or alternative workspaces in bytes
   */
  public long memoryOverhead(boolean cuDNN) {
    return 0;
  }

  /**
   * Get a string representation of the parameters of this layer for the
   * <code>toString</code> method. Override this method if your NetworkLayer
   * specialization shall print additional information besides name, input
   * and output blobs.
   *
   * @return additional parameters that are appended to the string
   *   representation of this layer
   */
  public String paramString() {
    return "";
  }

  /**
   * Get a string representation of this <code>NetworkLayer</code> for
   * console output. The string reports the layer's name and input and
   * output blobs. If the concrete implementation overloads the
   * <code>paramString</code> method the returned string is appended.
   *
   * @return a string representation of this <code>NetworkLayer</code>
   */
  @Override
  public String toString() {
    String res = layerTypeString() + " " + name() + " {";
    if (_in != null) {
      res += " in:";
      for (CaffeBlob blob : _in) res += " " + blob;
    }
    if (_out != null) {
      res += " out:";
      for (CaffeBlob blob : _out) res += " " + blob;
    }
    String params = paramString();
    res += (params.equals("") ? "" : (" " + paramString())) + " }";
    return res;
  }

  private final Net _net;
  private final CaffeBlob[] _in;

  /**
   * The output blob array of this <code>NetworkLayer</code>. The constructor
   * resizes the array according to the given network parameters, but the
   * concrete implementations are responsible for creating the
   * <code>CaffeBlob</code> instances and adding them to this array.
   */
  protected final CaffeBlob[] _out;

  /**
   * The layer parameters as compiled protobuf object
   */
  protected final Caffe.LayerParameter _layerParam;

}
