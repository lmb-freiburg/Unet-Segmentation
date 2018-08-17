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

/**
 * PoolingLayer provides functionality to compute the required
 * memory of the corresponding caffe PoolingLayer.
 *
 * @author Thorsten Falk
 * @version 1.0
 * @since 1.0
 */
public class PoolingLayer extends NetworkLayer {

  /**
   * Create a new <code>PoolingLayer</code> object.
   *
   * @param layerParam the parameters used to setup the layer in compiled
   *   protocol buffer format
   * @param net the parent <code>Net</code> object
   * @param in the input blobs for this layer
   *
   * @throws BlobException if the stride and pad combination is invalid for
   *   any input blob shape
   *
   * @see caffe.Caffe.PoolingParameter
   */
  public PoolingLayer(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in)
      throws BlobException {
    super(layerParam, net, in);
    Caffe.PoolingParameter cp = layerParam.getPoolingParam();
    _kernelShape = new int[in[0].shape().length - 2];
    for (int d = 0; d < cp.getKernelSizeCount(); ++d)
        _kernelShape[d] = cp.getKernelSize(d);
    for (int d = cp.getKernelSizeCount(); d < _kernelShape.length; ++d)
        _kernelShape[d] = _kernelShape[d - 1];
    _pad = new int[in[0].shape().length - 2];
    if (cp.getPadCount() > 0) {
      for (int d = 0; d < cp.getPadCount(); ++d)
          _pad[d] = cp.getPad(d);
      for (int d = cp.getPadCount(); d < _pad.length; ++d)
          _pad[d] = _pad[d - 1];
    }
    else for (int d = 0; d < _pad.length; ++d) _pad[d] = 0;
    _stride = new int[in[0].shape().length - 2];
    if (cp.getStrideCount() > 0) {
      for (int d = 0; d < cp.getStrideCount(); ++d)
          _stride[d] = cp.getStride(d);
      for (int d = cp.getStrideCount(); d < _stride.length; ++d)
          _stride[d] = _stride[d - 1];
    }
    else for (int d = 0; d < _stride.length; ++d) _stride[d] = 1;

    long[] outShape = new long[in[0].shape().length];
    outShape[0] = in[0].nSamples();
    outShape[1] = in[0].nChannels();
    int padSum = 0;
    for (int d = 0; d < _kernelShape.length; ++d) {
      outShape[d + 2] = (int)Math.ceil(
          (float)(in[0].shape()[d + 2] + 2 * _pad[d] - _kernelShape[d]) /
          (float)_stride[d]) + 1;
      padSum += _pad[d];
    }
    if (padSum > 0) {
      for (int d = 0; d < _kernelShape.length; ++d) {
        if ((outShape[d + 2] - 1) * _stride[d] >=
            in[0].shape()[d + 2] + _pad[d])
            --outShape[d + 2];
        if ((outShape[d + 2] - 1) * _stride[d] >=
            in[0].shape()[d + 2] + _pad[d])
            throw new BlobException("Invalid pooling parameters given");
      }
    }
    _out[0] = new CaffeBlob(
        layerParam.getTop(0), outShape, this, true, in[0].gradientRequired());
    for (CaffeBlob blob : in) blob.setOnGPU(true);
  }

  /**
   * {@inheritDoc}
   *
   * @return a string representation of kernel shape, padding and stride
   */
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

  /**
   * {@inheritDoc}
   * <p>
   * For gradient propagation and unpooling operations, the index for each
   * local pooling operation is stored leading to an overhead corresponding
   * the the output blob shape.
   *
   * @return {@inheritDoc}
   */
  @Override
  public long memoryOther() {
    return 4 * (_out[0].count() + 4 * _kernelShape.length +
                (_kernelShape.length + 1));
  }

  private final int[] _kernelShape;
  private final int[] _pad;
  private final int[] _stride;
}
