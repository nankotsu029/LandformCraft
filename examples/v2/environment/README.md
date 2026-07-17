# V2-4 water-condition field example

`water-condition-plan-v2.json` はV2-4-05の400×400最小contract例です。最終地形／水系接続とclimate moistureから、drainage/water distance、groundwater proxy、tidal influence、salinity、hydroperiod、wetness、wetness residualの7 fieldをinteger-onlyで導出します。

距離は最大64 blockのbounded supportに限定し、marine connectivityが明示されない限りsalinity／tidal influenceを0にします。implicit ocean fallbackとunbounded diffusionは拒否します。mangrove shaping、coral bathymetry、ecology placement、cave-local moisture、sidecar／Release capabilityは含みません。
