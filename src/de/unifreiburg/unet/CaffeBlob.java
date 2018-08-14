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

public class CaffeBlob {

  public CaffeBlob(String name, long[] shape, NetworkLayer layer) {
    this(name, shape, layer, false);
  }

  public CaffeBlob(
      String name, long[] shape, NetworkLayer layer, boolean onGPU) {
    this(name, shape, layer, false, false);
  }

  public CaffeBlob(
      String name, long[] shape, NetworkLayer layer, boolean onGPU,
      boolean gradientRequired) {
    this(name, shape, layer, onGPU, gradientRequired, true);
  }

  public CaffeBlob(
      String name, long[] shape, NetworkLayer layer, boolean onGPU,
      boolean gradientRequired, boolean forwardRequired) {
    _name = name;
    _shape = shape;
    _layer = layer;
    _onGPU = onGPU;
    _forwardRequired = forwardRequired;
    _gradientRequired = gradientRequired;
  }

  public String name() {
    return _name;
  }

  public long[] shape() {
    return _shape;
  }

  public long nSamples() {
    return _shape[0];
  }

  public long nChannels() {
    return _shape[1];
  }

  public NetworkLayer layer() {
    return _layer;
  }

  public long count() {
    return count(0);
  }

  public long count(int from) {
    return count(from, _shape.length - 1);
  }

  public long count(int from, int to) {
    long res = 1;
    for (int d = from; d <= to; ++d) res *= _shape[d];
    return res;
  }

  void setOnGPU(boolean onGPU) {
    _onGPU = onGPU;
  }

  boolean onGPU() {
    return _onGPU;
  }

  void setGradientRequired(boolean gradientRequired) {
    _gradientRequired = gradientRequired;
  }

  boolean forwardRequired() {
    return _forwardRequired;
  }

  boolean gradientRequired() {
    return _gradientRequired;
  }

  long memoryForward() {
    return (_onGPU && _forwardRequired) ? 4 * count() : 0;
  }

  long memoryBackward() {
    return (_onGPU && _gradientRequired) ? 4 * count() : 0;
  }

  @Override
  public String toString() {
    String res = _name + " [";
    for (int i = 0; i < _shape.length - 1; ++i) res += _shape[i] + ",";
    res += _shape[_shape.length - 1] + "]" +
        ((_onGPU || _forwardRequired || _gradientRequired) ? "{" : "") +
        (_onGPU ? "*" : "") + (_forwardRequired ? "F" : "") +
        (_gradientRequired ? "B" : "") +
        ((_onGPU || _forwardRequired || _gradientRequired) ? "}" : "");
    return res;
  }

  private final String _name;
  private final long[] _shape;
  private final NetworkLayer _layer;
  private boolean _onGPU;
  private boolean _forwardRequired;
  private boolean _gradientRequired;

}
