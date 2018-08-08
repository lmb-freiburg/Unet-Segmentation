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

public class ConvolutionLayer extends NetworkLayer {

  public ConvolutionLayer(
      String name, Net net, CaffeBlob[] in, String topName, int[] kernelShape,
      int[] pad, int[] stride, int[] dilation, int nChannels)
      throws BlobException {
    super(name, net, in, new CaffeBlob[1]);
    _kernelShape = kernelShape;
    _pad = pad;
    _stride = stride;
    _dilation = dilation;

    if (net.findBlob(topName) != null) throw new BlobException(
        "In-place convolution not implemented");

    int[] outputShape = new int[in[0].shape().length];
    outputShape[0] = in[0].nSamples();
    outputShape[1] = nChannels;
    for (int d = 0; d < kernelShape.length; ++d) {
      int numerator = in[0].shape()[d + 2] + 2 * pad[d] -
          (dilation[d] * (kernelShape[d] - 1) + 1);
      if (numerator <= 0) throw new BlobException(
          "Convolution would reduce output blob size to zero");
      if (numerator % stride[d] != 0) throw new BlobException(
          "Invalid stride for convolution");
      outputShape[d + 2] = numerator / stride[d] + 1;
    }
    _out[0] = new CaffeBlob(topName, outputShape, this);

    long kernelSize = 1;
    for (int extent: kernelShape) kernelSize *= extent;
    _memPara = 4 * nChannels * (in[0].nChannels() * kernelSize + 1);
    _memOverheadCuDNN = 8 * 1024 * 1024;
    _memOverheadNoCuDNN = (kernelSize > 0) ?
        4 * _out[0].count(2) * in[0].nChannels() * kernelSize : 0;
  }

  public static NetworkLayer createFromProto(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in)
      throws BlobException {
    Caffe.ConvolutionParameter cp = layerParam.getConvolutionParam();
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
    int[] dilation = new int[in[0].shape().length - 2];
    if (cp.getDilationCount() > 0) {
      for (int d = 0; d < cp.getDilationCount(); ++d)
          dilation[d] = cp.getDilation(d);
      for (int d = cp.getDilationCount(); d < dilation.length; ++d)
          dilation[d] = dilation[d - 1];
    }
    else for (int d = 0; d < dilation.length; ++d) dilation[d] = 1;
    int nChannels = cp.getNumOutput();
    return new ConvolutionLayer(
        layerParam.getName(), net, in, layerParam.getTop(0),
        kernelShape, pad, stride, dilation, nChannels);
  }

  @Override
  public String layerTypeString() { return "ConvolutionLayer"; }

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
    res += " dilation: [ ";
    for (int extent : _dilation) res += extent + " ";
    res += "]";
    return res;
  }

  @Override
  public long memoryConsumptionParameters() {
    return _memPara;
  }

  @Override
  public long memoryOverhead(boolean cuDNN) {
    return cuDNN ? _memOverheadCuDNN : _memOverheadNoCuDNN;
  }

  private final int[] _kernelShape;
  private final int[] _pad;
  private final int[] _stride;
  private final int[] _dilation;

  private final long _memPara;
  private final long _memOverheadCuDNN;
  private final long _memOverheadNoCuDNN;

}
