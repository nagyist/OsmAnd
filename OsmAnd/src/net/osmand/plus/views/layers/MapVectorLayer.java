package net.osmand.plus.views.layers;

import net.osmand.core.android.MapRendererView;
import net.osmand.core.jni.MapLayerConfiguration;
import net.osmand.core.jni.PointI;
import net.osmand.data.LatLon;
import net.osmand.data.QuadPointDouble;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.render.MapRenderRepositories;
import net.osmand.plus.resources.ResourceManager;
import net.osmand.plus.views.layers.base.BaseMapLayer;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.corenative.NativeCoreContext;
import net.osmand.util.MapUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;

public class MapVectorLayer extends BaseMapLayer {
	private OsmandMapTileView view;
	private ResourceManager resourceManager;
	private Paint paintImg;

	private final int VECTOR_LAYER_ID = 1;
	private final RectF destImage = new RectF();
	private boolean visible = false;
	private boolean oldRender = false;

	public MapVectorLayer(@NonNull Context context, boolean oldRender) {
		super(context);
		this.oldRender = oldRender;
	}

	@Override
	public void destroyLayer() {
		if (view != null) {
			final MapRendererView mapRenderer = view.getMapRenderer();
			if (mapRenderer != null) {
				mapRenderer.resetMapLayerProvider(VECTOR_LAYER_ID);
			}
		}
	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	@Override
	public void initLayer(@NonNull OsmandMapTileView view) {
		this.view = view;
		resourceManager = view.getApplication().getResourceManager();
		paintImg = new Paint();
		paintImg.setFilterBitmap(true);

		setAlpha(getAlpha());
	}

	public boolean isVectorDataVisible() {
		return visible && view.getZoom() >= view.getSettings().LEVEL_TO_SWITCH_VECTOR_RASTER.get();
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;

		if (!visible) {
			if (view != null) {
				final MapRendererView mapRenderer = view.getMapRenderer();
				if (mapRenderer != null) {
					mapRenderer.resetMapLayerProvider(VECTOR_LAYER_ID);
				}
			}
		}
	}

	@Override
	public int getMaximumShownMapZoom() {
		return 22;
	}

	@Override
	public int getMinimumShownMapZoom() {
		return 1;
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tilesRect, DrawSettings drawSettings) {

	}

	@Override
	public void onPrepareBufferImage(Canvas canvas, RotatedTileBox tilesRect, DrawSettings drawSettings) {
		if (!visible) {
			return;
		}
		// if (!isVectorDataVisible() && tileLayer != null) {
		// tileLayer.drawTileMap(canvas, tilesRect);
		// resourceManager.getRenderer().interruptLoadingMap();
		// } else {
		final MapRendererView mapRenderer = view.getMapRenderer();
		if (mapRenderer != null && !oldRender) {
			NativeCoreContext.getMapRendererContext().setNightMode(drawSettings.isNightMode());

			// opengl renderer
			LatLon ll = tilesRect.getLatLonFromPixel(tilesRect.getPixWidth() / 2, tilesRect.getPixHeight() / 2);
			mapRenderer.setTarget(new PointI(MapUtils.get31TileNumberX(ll.getLongitude()), MapUtils.get31TileNumberY(ll
					.getLatitude())));
			mapRenderer.setAzimuth(-tilesRect.getRotate());
			mapRenderer.setZoom((float) (tilesRect.getZoom() + tilesRect.getZoomAnimation() + tilesRect
					.getZoomFloatPart()));
			float zoomMagnifier = getMapDensity();
			mapRenderer.setVisualZoomShift(zoomMagnifier - 1.0f);
		} else {
			if (!view.isZooming()) {
				if (resourceManager.updateRenderedMapNeeded(tilesRect, drawSettings)) {
					// pixRect.set(-view.getWidth(), -view.getHeight() / 2, 2 * view.getWidth(), 3 *
					// view.getHeight() / 2);
					final RotatedTileBox copy = tilesRect.copy();
					copy.increasePixelDimensions(copy.getPixWidth() / 3, copy.getPixHeight() / 4);
					resourceManager.updateRendererMap(copy, null);
				}

			}

			MapRenderRepositories renderer = resourceManager.getRenderer();
			drawRenderedMap(canvas, renderer.getBitmap(), renderer.getBitmapLocation(), tilesRect);
			drawRenderedMap(canvas, renderer.getPrevBitmap(), renderer.getPrevBmpLocation(), tilesRect);
		}
	}

	private boolean drawRenderedMap(Canvas canvas, Bitmap bmp, RotatedTileBox bmpLoc, RotatedTileBox currentViewport) {
		boolean shown = false;
		if (bmp != null && bmpLoc != null) {
			float rot = -bmpLoc.getRotate();
			canvas.rotate(rot, currentViewport.getCenterPixelX(), currentViewport.getCenterPixelY());
			final RotatedTileBox calc = currentViewport.copy();
			calc.setRotate(bmpLoc.getRotate());
			QuadPointDouble lt = bmpLoc.getLeftTopTile(bmpLoc.getZoom());
			QuadPointDouble rb = bmpLoc.getRightBottomTile(bmpLoc.getZoom());
			final float x1 = calc.getPixXFromTile(lt.x, lt.y, bmpLoc.getZoom());
			final float x2 = calc.getPixXFromTile(rb.x, rb.y, bmpLoc.getZoom());
			final float y1 = calc.getPixYFromTile(lt.x, lt.y, bmpLoc.getZoom());
			final float y2 = calc.getPixYFromTile(rb.x, rb.y, bmpLoc.getZoom());
			
//			LatLon lt = bmpLoc.getLeftTopLatLon();
//			LatLon rb = bmpLoc.getRightBottomLatLon();
//			final float x1 = calc.getPixXFromLatLon(lt.getLatitude(), lt.getLongitude());
//			final float x2 = calc.getPixXFromLatLon(rb.getLatitude(), rb.getLongitude());
//			final float y1 = calc.getPixYFromLatLon(lt.getLatitude(), lt.getLongitude());
//			final float y2 = calc.getPixYFromLatLon(rb.getLatitude(), rb.getLongitude());
			destImage.set(x1, y1, x2, y2);
			if (!bmp.isRecycled()) {
				canvas.drawBitmap(bmp, null, destImage, paintImg);
				shown = true;
			}
			canvas.rotate(-rot, currentViewport.getCenterPixelX(), currentViewport.getCenterPixelY());
		}
		return shown;
	}

	@Override
	public void setAlpha(int alpha) {
		if (getAlpha() == alpha){
			return;
		}

		super.setAlpha(alpha);
		if (paintImg != null) {
			paintImg.setAlpha(alpha);
		}

		if (view == null) {
			return;
		}

		final MapRendererView mapRenderer = view.getMapRenderer();
		if (mapRenderer != null) {
			MapLayerConfiguration mapLayerConfiguration = new MapLayerConfiguration();
			mapLayerConfiguration.setOpacityFactor(((float) alpha) / 255.0f);
			mapRenderer.setMapLayerConfiguration(VECTOR_LAYER_ID, mapLayerConfiguration);
		}
	}

	@Override
	public boolean onLongPressEvent(PointF point, RotatedTileBox tileBox) {
		return false;
	}

	@Override
	public boolean onSingleTap(PointF point, RotatedTileBox tileBox) {
		return false;
	}
}
