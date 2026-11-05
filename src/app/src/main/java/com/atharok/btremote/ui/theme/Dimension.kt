package com.atharok.btremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.atharok.btremote.R

@Composable
@ReadOnlyComposable
fun surfaceElevationLow(): Dp =
    if(MaterialTheme.colorScheme.background == Color.Black)
        dimensionResource(R.dimen.true_black_surface_elevation_low)
    else
        dimensionResource(id = R.dimen.surface_elevation_low)

@Composable
@ReadOnlyComposable
fun surfaceElevationMedium(): Dp =
    if(MaterialTheme.colorScheme.background == Color.Black)
        dimensionResource(R.dimen.true_black_surface_elevation_medium)
    else
        dimensionResource(id = R.dimen.surface_elevation_medium)

@Composable
@ReadOnlyComposable
fun surfaceElevationHigh(): Dp =
    if(MaterialTheme.colorScheme.background == Color.Black)
        dimensionResource(R.dimen.true_black_surface_elevation_high)
    else
        dimensionResource(id = R.dimen.surface_elevation_high)

@Composable
@ReadOnlyComposable
fun surfaceElevationShadow(): Dp = dimensionResource(id = R.dimen.surface_elevation_shadow)