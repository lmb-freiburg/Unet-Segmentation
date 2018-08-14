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

public abstract class NetworkLayer {

  public NetworkLayer(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in) {
    _layerParam = layerParam;
    _net = net;
    _in = in;
    _out = new CaffeBlob[layerParam.getTopCount()];
  }

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

  public String layerTypeString() {
    return _layerParam.getType();
  }


  public final String name() {
    return _layerParam.getName();
  }

  public final CaffeBlob[] inputBlobs() {
    return _in;
  }

  public final CaffeBlob[] outputBlobs() {
    return _out;
  }

  public long memoryParameters() {
    return 0;
  }

  public long memoryOther() {
    return 0;
  }

  public long memoryOverhead(boolean cuDNN) {
    return 0;
  }

  public String paramString() {
    return "";
  }

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
  protected final CaffeBlob[] _out;
  protected final Caffe.LayerParameter _layerParam;

}
