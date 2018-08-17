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
 * CreateDeformationLayer provides functionality to compute the required
 * memory of the corresponding caffe CreateDeformationLayer.
 *
 * @author Thorsten Falk
 * @version 1.0
 * @since 1.0
 */
public class CreateDeformationLayer extends NetworkLayer {

  /**
   * Create a new <code>CreateDeformationLayer</code> object.
   *
   * @param layerParam the parameters used to setup the layer in compiled
   *   protocol buffer format
   * @param net the parent <code>Net</code> object
   * @param in the input blobs for this layer
   *
   * @see caffe.Caffe.CreateDeformationParameter
   */
  public CreateDeformationLayer(
      Caffe.LayerParameter layerParam, Net net, CaffeBlob[] in) {
    super(layerParam, net, in);
    Caffe.CreateDeformationParameter cp =
        layerParam.getCreateDeformationParam();
    int nDims = (cp.hasNz() && cp.getNz() > 0) ? 3 : 2;
    long[] topShape = new long[nDims + 2];
    topShape[0] = (in != null && in.length > 0) ? in[0].nSamples() :
        cp.getBatchSize();
    topShape[1] = (nDims == 3) ? cp.getNz() : cp.getNy();
    topShape[2] = (nDims == 3) ? cp.getNy() : cp.getNx();
    topShape[3] = (nDims == 3) ? cp.getNx() : cp.getNcomponents();
    if (nDims == 3) topShape[4] = cp.getNcomponents();
    _out[0] = new CaffeBlob(layerParam.getTop(0), topShape, this);
  }
}
