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

  public NetworkLayer(String name, Net net, CaffeBlob[] in, CaffeBlob[] out) {
    _name = name;
    _net = net;
    _in = in;
    _out = out;
  }

  public static NetworkLayer createFromProto(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in)
      throws NotImplementedException, BlobException {
    if (layerParam.getType().equals("ValueTransformation"))
        return ValueTransformationLayer.createFromProto(layerParam, net, in);
    if (layerParam.getType().equals("Convolution"))
        return ConvolutionLayer.createFromProto(layerParam, net, in);
    else if (layerParam.getType().equals("ReLU"))
        return ReLULayer.createFromProto(layerParam, net, in);
    else if (layerParam.getType().equals("Pooling"))
        return PoolingLayer.createFromProto(layerParam, net, in);
    else if (layerParam.getType().equals("Deconvolution"))
        return UpConvolutionLayer.createFromProto(layerParam, net, in);
    else if (layerParam.getType().equals("Concat"))
        return ConcatAndCropLayer.createFromProto(layerParam, net, in);
    else throw new NotImplementedException(
        "Layer type " + layerParam.getType() + " not implemented");
  }

  public abstract String layerTypeString();

  public String paramString() {
    return "";
  }

  public final String name() {
    return _name;
  }

  public final CaffeBlob[] inputBlobs() {
    return _in;
  }

  public final CaffeBlob[] outputBlobs() {
    return _out;
  }

  public long memoryConsumptionParameters() {
    return 0;
  }

  public long memoryOverhead(boolean cuDNN) {
    return 0;
  }

  public final long memoryConsumption(boolean cuDNN) {
    return memoryConsumptionParameters() + memoryOverhead(cuDNN);
  }

  public String toString() {
    String res = layerTypeString() + " " + _name + " {";
    if (_in != null) {
      res += " in: ";
      for (CaffeBlob blob : _in) {
        res += blob.name() + " [ ";
        for (int extent: blob.shape()) res += extent + " ";
        res += "]";
      }
    }
    if (_out != null) {
      res += " out: ";
      for (CaffeBlob blob : _out) {
        res += blob.name() + " [ ";
        for (int extent: blob.shape()) res += extent + " ";
        res += "]";
      }
    }
    res += " " + paramString() + " }";
    return res;
  }

  private final String _name;
  private final Net _net;
  private final CaffeBlob[] _in;
  protected final CaffeBlob[] _out;

}
