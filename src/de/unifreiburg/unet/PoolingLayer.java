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

public class PoolingLayer extends NetworkLayer {

  public PoolingLayer(
      String name, Net net, CaffeBlob[] in, String topName, int[] kernelShape,
      int[] pad, int[] stride)
      throws BlobException {
    super(name, net, in, new CaffeBlob[1]);
    _kernelShape = kernelShape;
    _pad = pad;
    _stride = stride;

    if (net.findBlob(topName) != null) throw new BlobException(
        "In-place pooling not implemented");

    int[] outShape = new int[in[0].shape().length];
    outShape[0] = in[0].nSamples();
    outShape[1] = in[0].nChannels();
    int padSum = 0;
    for (int d = 0; d < kernelShape.length; ++d) {
      outShape[d + 2] = (int)Math.ceil(
          (float)(in[0].shape()[d + 2] + 2 * pad[d] - kernelShape[d]) /
          (float)stride[d]) + 1;
      padSum += pad[d];
    }
    if (padSum > 0) {
      for (int d = 0; d < kernelShape.length; ++d) {
        if ((outShape[d + 2] - 1) * stride[d] >= in[0].shape()[d + 2] + pad[d])
            --outShape[d + 2];
        if ((outShape[d + 2] - 1) * stride[d] >= in[0].shape()[d + 2] + pad[d])
            throw new BlobException("Invalid pooling parameters given");
      }
    }
    _out[0] = new CaffeBlob(topName, outShape, this, true);

    for (CaffeBlob blob : in) blob.setOnGPU(true);
  }

  public static NetworkLayer createFromProto(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in)
      throws BlobException {
    Caffe.PoolingParameter cp = layerParam.getPoolingParam();
    int[] kernelShape = new int[in[0].shape().length - 2];
    for (int d = 0; d < cp.getKernelSizeCount(); ++d)
        kernelShape[d] = cp.getKernelSize(d);
    for (int d = cp.getKernelSizeCount(); d < kernelShape.length; ++d)
        kernelShape[d] = kernelShape[d - 1];
    int[] pad = new int[in[0].shape().length - 2];
    if (cp.getPadCount() > 0) {
      for (int d = 0; d < cp.getPadCount(); ++d)
          pad[d] = cp.getPad(d);
      for (int d = cp.getPadCount(); d < pad.length; ++d)
          pad[d] = pad[d - 1];
    }
    else for (int d = 0; d < pad.length; ++d) pad[d] = 0;
    int[] stride = new int[in[0].shape().length - 2];
    if (cp.getStrideCount() > 0) {
      for (int d = 0; d < cp.getStrideCount(); ++d)
          stride[d] = cp.getStride(d);
      for (int d = cp.getStrideCount(); d < stride.length; ++d)
          stride[d] = stride[d - 1];
    }
    else for (int d = 0; d < stride.length; ++d) stride[d] = 1;
    return new PoolingLayer(
        layerParam.getName(), net, in, layerParam.getTop(0),
        kernelShape, pad, stride);
  }

  @Override
  public String layerTypeString() { return "PoolingLayer"; }

  @Override
  public String paramString() {
    String res = "kernelShape: [ ";
    for (int extent : _kernelShape) res += extent + " ";
    res += "]";
    res += " pad: [ ";
    for (int extent : _pad) res += extent + " ";
    res += "]";
    res += " stride: [ ";
    for (int extent : _stride) res += extent + " ";
    res += "]";
    return res;
  }

  private final int[] _kernelShape;
  private final int[] _pad;
  private final int[] _stride;

}
