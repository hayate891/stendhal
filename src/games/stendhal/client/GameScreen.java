/* $Id$ */
/***************************************************************************
 *                      (C) Copyright 2003 - Marauroa                      *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.client;

import games.stendhal.client.entity.Entity;
import games.stendhal.client.gui.j2DClient;
import games.stendhal.client.gui.j2d.Text;
import games.stendhal.client.gui.j2d.entity.Entity2DView;
import games.stendhal.client.gui.j2d.entity.Entity2DViewFactory;
import games.stendhal.client.gui.wt.core.WtBaseframe;
import games.stendhal.client.sprite.ImageSprite;
import games.stendhal.client.sprite.Sprite;
import games.stendhal.client.sprite.SpriteStore;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Transparency;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferStrategy;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import marauroa.common.Log4J;
import marauroa.common.Logger;

/**
 * This class is an abstraction of the game screen, so that we can think of it
 * as a window to the world, we can move it, place it and draw object usings
 * World coordinates. This class is based on the singleton pattern.
 */
public class GameScreen {

	/** the logger instance. */
	private static final Logger logger = Log4J.getLogger(GameScreen.class);

	/** The width / height of one tile. */
	public final static int SIZE_UNIT_PIXELS = 32;

	/**
	 * Comparator used to sort entities to display.
	 */
	protected static final EntityViewComparator	entityViewComparator = new EntityViewComparator();

	/**
	 * A scale factor for panning delta (to allow non-float precision).
	 */
	protected static final int	PAN_SCALE	= 8;

	private BufferStrategy strategy;

	private Graphics2D g;

	/**
	 * Client.
	 */
	protected StendhalClient	client;

	/**
	 * The text bubbles.
	 */
	private LinkedList<Text> texts;

	/**
	 * The text bubbles to remove.
	 */
	private List<Text> textsToRemove;

	/**
	 * The entity views.
	 */
	protected List<Entity2DView>	views;

	/**
	 * The entity to view map.
	 */
	protected Map<Entity, Entity2DView>	entities;

	private static Sprite offlineIcon;

	private boolean offline;

	private int blinkOffline;

	/**
	 * The targeted center of view X coordinate (truncated).
	 */
	private int	x;

	/**
	 * The targeted center of view Y coordinate (truncated).
	 */
	private int	y;

	/** Actual size of the screen in pixels */
	private int sw, sh;

	/** Actual size of the world in world units */
	protected int ww, wh;

	/** the singleton instance */
	private static GameScreen screen;

	/** the awt-component which this screen belongs to */
	private Component component;

	/**
	 * The difference between current and target screen view X.
	 */
	private int	dvx;

	/**
	 * The difference between current and target screen view Y.
	 */
	private int	dvy;

	/**
	 * The current screen view X.
	 */
	private int	svx;

	/**
	 * The current screen view Y.
	 */
	private int	svy;

	/**
	 * The pan speed.
	 */
	private int	speed;


	static {
		offlineIcon = SpriteStore.get().getSprite("data/gui/offline.png");
	}


	/**
	 * Set the default [singleton] screen.
	 *
	 * @param	screen		The screen.
	 */
	public static void setDefaultScreen(GameScreen screen) {
		GameScreen.screen = screen;
	}

	/** Returns the GameScreen object */
	public static GameScreen get() {
		return screen;
	}

	/** sets the awt-component which this screen belongs to */
	public void setComponent(Component component) {
		this.component = component;
	}

	/** returns the awt-component which this screen belongs to */
	public Component getComponent() {
		return component;
	}

	/** Returns screen width in world units */
	public double getViewWidth() {
		return sw / SIZE_UNIT_PIXELS;
	}

	/** Returns screen height in world units */
	public double getViewHeight() {
		return sh / SIZE_UNIT_PIXELS;
	}

	public GameScreen(StendhalClient client, BufferStrategy strategy, int sw, int sh) {
		this.client = client;
		this.strategy = strategy;
		this.sw = sw;
		this.sh = sh;

		x = 0;
		y = 0;
		svx = sw / -2;
		svy = sh / -2;
		dvx = 0;
		dvy = 0;

		speed = 0;

		texts = new LinkedList<Text>();
		textsToRemove = new LinkedList<Text>();
		views = new LinkedList<Entity2DView>();
		entities = new HashMap<Entity, Entity2DView>();

		g = (Graphics2D) strategy.getDrawGraphics();
	}


	/** Prepare screen for the next frame to be rendered and move it if needed */
	public void nextFrame() {
		g.dispose();
		strategy.show();

		adjustView();

		g = (Graphics2D) strategy.getDrawGraphics();
	}


	/**
	 * Add an entity.
	 *
	 * @param	entity		An entity.
	 */
	public void addEntity(Entity entity) {
		Entity2DView view = createView(entity);

		if(view != null) {
			entities.put(entity, view);
			addEntityView(view);
		}
	}


	/**
	 * Add an entity view.
	 *
	 * @param	view		A view.
	 */
	protected void addEntityView(Entity2DView view) {
		views.add(view);

		view.setInspector(StendhalUI.get().getInspector());
	}


	/**
	 * Remove an entity.
	 *
	 * @param	entity		An entity.
	 */
	public void removeEntity(final Entity entity) {
		Entity2DView view = entities.remove(entity);

		if(view != null) {
			removeEntityView(view);
		}
	}


	/**
	 * Remove an entity view.
	 *
	 * @param	view		A view.
	 */
	protected void removeEntityView(Entity2DView view) {
		view.release();
		views.remove(view);
	}


	/**
	 * Returns the Graphics2D object in case you want to operate it directly.
	 * Ex. GUI
	 */
	public Graphics2D expose() {
		return g;
	}


	/**
	 * Update the view position to center the target position.
	 */
	protected void adjustView() {
		/*
		 * Already centered?
		 */
		if((dvx == 0) && (dvy == 0)) {
			return;
		}

		int sx = convertWorldXToScreenView(x) + (SIZE_UNIT_PIXELS / 2);
		int sy = convertWorldYToScreenView(y) + (SIZE_UNIT_PIXELS / 2);

		if((sx < 0) || (sx >= sw) || (sy < -SIZE_UNIT_PIXELS) || (sy > sh)) {
			/*
			 * If off screen, just center
			 */
			center();
		} else {
			/*
			 * Calculate the target speed.
			 * The farther away, the faster.
			 */
			int dux = dvx / 40;
			int duy = dvy / 40;

			int tspeed = ((dux * dux) + (duy * duy)) * PAN_SCALE;

			if(speed > tspeed) {
				speed = (speed + speed + tspeed) / 3;

				/*
				 * Don't stall
				 */
				if((dvx != 0) || (dvy != 0)) {
					speed = Math.max(speed, 1);
				}
			} else if(speed < tspeed) {
				speed += 2;
			}

			/*
			 * Moving?
			 */
			if(speed != 0) {
				/*
				 * Not a^2 + b^2 = c^2, but good enough
				 */
				int scalediv = (Math.abs(dvx) + Math.abs(dvy)) * PAN_SCALE;

				int dx = speed * dvx / scalediv;
				int dy = speed * dvy / scalediv;

				/*
				 * Don't overshoot.
				 * Don't stall.
				 */
				if(dvx < 0) {
					if(dx == 0) {
						dx = -1;
					} else if(dx < dvx) {
						dx = dvx;
					}
				} else if(dvx > 0) {
					if(dx == 0) {
						dx = 1;
					} else if(dx > dvx) {
						dx = dvx;
					}
				}

				if(dvy < 0) {
					if(dy == 0) {
						dy = -1;
					} else if(dy < dvy) {
						dy = dvy;
					}
				} else if(dvy > 0) {
					if(dy == 0) {
						dy = 1;
					} else if(dy > dvy) {
						dy = dvy;
					}
				}

				/*
				 * Adjust view
				 */
				svx += dx;
				dvx -= dx;

				svy += dy;
				dvy -= dy;
			}
		}
	}


	/**
	 * Update the view position to center the target position.
	 *
	 * @param	immediate	Center on the coodinates immediately.
	 */
	protected void calculateView() {
		int cvx = (x * SIZE_UNIT_PIXELS) + (SIZE_UNIT_PIXELS / 2) - (sw / 2);
		int cvy = (y * SIZE_UNIT_PIXELS) + (SIZE_UNIT_PIXELS / 2) - (sh / 2);

		/*
		 * Keep the world with-in the screen view
		 */
		if(cvx < 0) {
			cvx = 0;
		} else {
			int max = (ww * SIZE_UNIT_PIXELS) - sw;

			if(cvx > max) {
				cvx = max;
			}
		}

		if(cvy < 0) {
			cvy = 0;
		} else {
			int max = (wh * SIZE_UNIT_PIXELS) - sh;

			if(cvy > max) {
				cvy = max;
			}
		}

		dvx = cvx - svx;
		dvy = cvy - svy;
	}


	/**
	 * Center the view.
	 */
	public void center() {
		svx += dvx;
		svy += dvy;

		dvx = 0;
		dvy = 0;

		speed = 0;
	}


	public Entity2DView createView(final Entity entity) {
		return Entity2DViewFactory.get().create(entity);
	}


	/*
	 * Draw the screen.
	 */
	public void draw(WtBaseframe baseframe) {
		Collections.sort(views, entityViewComparator);

		/*
		 * Draw the GameLayers from bootom to top, relies on exact
		 * naming of the layers
		 */
		StaticGameLayers gameLayers = client.getStaticGameLayers();
		String set = gameLayers.getRPZoneLayerSet();

		int x = (int) getViewX();
		int y = (int) getViewY();
		int w = (int) getViewWidth();
		int h = (int) getViewHeight();

		/*
		 * End of the world (map falls short of the view)?
		 */
		int px = convertWorldXToScreenView(Math.max(x, 0));

		if(px > 0) {
			g.setColor(Color.black);
			g.fillRect(0, 0, px, sh);
		}

		px = convertWorldXToScreenView(Math.min(x + w, ww));

		if(px < sw) {
			g.setColor(Color.black);
			g.fillRect(px, 0, sw - px, sh);
		}

		int py = convertWorldYToScreenView(Math.max(y, 0));

		if(py > 0) {
			g.setColor(Color.black);
			g.fillRect(0, 0, sw, py);
		}

		py = convertWorldYToScreenView(Math.min(y + h, wh));

		if(py < sh) {
			g.setColor(Color.black);
			g.fillRect(0, py, sw, sh - py);
		}

		/*
		 * Layers
		 */
		gameLayers.draw(this, set, "0_floor", x, y, w, h);
		gameLayers.draw(this, set, "1_terrain", x, y, w, h);
		gameLayers.draw(this, set, "2_object", x, y, w, h);
		drawEntities();
		gameLayers.draw(this, set, "3_roof", x, y, w, h);
		gameLayers.draw(this, set, "4_roof_add", x, y, w, h);
		drawTopEntities();
		drawText();

		/*
		 * Dialogs
		 */
		baseframe.draw(g);

		/*
		 * Offline
		 */
		if (offline && (blinkOffline > 0)) {
			offlineIcon.draw(g, 560, 420);
		}

		if (blinkOffline < -10) {
			blinkOffline = 20;
		} else {
			blinkOffline--;
		}
	}


	/**
	 * Draw the screen entities.
	 */
	protected void drawEntities() {
		Graphics2D g2d = expose();

		for (Entity2DView view : views) {
			view.draw(g2d);
		}
	}


	/**
	 * Draw the top portion screen entities (such as HP/title bars).
	 */
	protected void drawTopEntities() {
		Graphics2D g2d = expose();

		for (Entity2DView view : views) {
			view.drawTop(g2d);
		}
	}


	/**
	 * Draw the screen text bubbles.
	 */
	protected void drawText() {
		texts.removeAll(textsToRemove);
		textsToRemove.clear();

		try {
			for (Text text : texts) {
				text.draw(this);
			}
		} catch (ConcurrentModificationException e) {
			logger.error("cannot draw text", e);
		}
	}

	/**
	 * Get the view X world coordinate.
	 *
	 * @return	The X coordinate of the left side.
	 */
	public double getViewX() {
		return (double) getScreenViewX() / SIZE_UNIT_PIXELS;
	}

	/**
	 * Get the view Y world coordinate.
	 *
	 * @return	The Y coordinate of the left side.
	 */
	public double getViewY() {
		return (double) getScreenViewY() / SIZE_UNIT_PIXELS;
	}

	/**
	 * Set the target coordinates that the screen centers on.
	 *
	 * @param	x		The world X coordinate.
	 * @param	y		The world Y coordinate.
	 */
	public void place(double x, double y) {
		int ix = (int) x;
		int iy = (int) y;

		/*
		 * Save CPU cycles
		 */
		if((ix != this.x) || (iy != this.y)) {
			this.x = ix;
			this.y = iy;

			calculateView();
		}
	}

	/**
	 * Sets the world size.
	 *
	 * @param	width		The world width.
	 * @param	height		The height width.
	 */
	public void setMaxWorldSize(double width, double height) {
		ww = (int) width;
		wh = (int) height;

		calculateView();
	}

	/**
	 * Set the offline indication state.
	 *
	 * @param	offline		<code>true</code> if offline.
	 */
	public void setOffline(boolean offline) {
		this.offline = offline;
	}


	/**
	 * Helper to get notification type color.
	 */
	protected Color getNotificationColor(NotificationType type) {
		return ((j2DClient) StendhalUI.get()).getNotificationColor(type);
	}


	/**
	 * Add a text bubble.
	 *
	 *
	 *
	 */
	public void addText(double x, double y, String text, NotificationType type, boolean isTalking) {
		addText(x, y, text, getNotificationColor(type), isTalking);
	}


	/**
	 * Add a text bubble.
	 *
	 *
	 *
	 */
	public void addText(final double x, final double y, final String text, final Color color, final boolean talking) {
		addText(convertWorldToScreen(x), convertWorldToScreen(y), text, color, talking);
	}


	/**
	 * Add a text bubble.
	 *
	 * @param	sx		The screen X coordinate.
	 * @param	sy		The screen Y coordinate.
	 * @param	text		The text.
	 * @param	type		The type of notification text.
	 * @param	talking		Is it is a talking text bubble.
	 */
	public void addText(final int sx, final int sy, final String text, final NotificationType type, final boolean talking) {
		addText(sx, sy, text, getNotificationColor(type), talking);
	}


	/**
	 * Add a text bubble.
	 *
	 * @param	sx		The screen X coordinate.
	 * @param	sy		The screen Y coordinate.
	 * @param	text		The text.
	 * @param	color		The text color.
	 * @param	talking		Is it is a talking text bubble.
	 */
	public void addText(int sx, int sy, final String text, final Color color, final boolean isTalking) {
		Sprite sprite = createTextBox(text, 240, color, Color.white, isTalking);

		if (isTalking) {
			// Point alignment: left, bottom
			sy -= sprite.getHeight();
		} else {
			// Point alignment: left-right centered, bottom
			sx -= (sprite.getWidth() / 2);
			sy -= sprite.getHeight();
                }


		/*
		 * Try to keep the text on screen.
		 * This could mess up the "talk" origin positioning.
		 */
		if(sx < 0) {
			sx = 0;
		} else {
			int max = getScreenWidth() - sprite.getWidth();

			if(sx > max) {
				sx = max;
			}
		}

		if(sy < 0) {
			sy = 0;
		} else {
			int max = getScreenHeight() - sprite.getHeight();

			if(sy > max) {
				sy = max;
			}
		}


		boolean found = true;

		while (found == true) {
			found = false;

			for (Text item : texts) {
				if ((item.getX() == sx) && (item.getY() == sy)) {
					found = true;
					sy += (SIZE_UNIT_PIXELS / 2);
					break;
				}
			}
		}

		texts.add(new Text(sprite, sx, sy,
			Math.max(Text.STANDARD_PERSISTENCE_TIME, text.length() * Text.STANDARD_PERSISTENCE_TIME / 50)));
	}


	/**
	 * Remove a text bubble.
	 */
	public void removeText(Text entity) {
		textsToRemove.add(entity);
	}


	/**
	 * Remove all objects from the screen.
	 */
	public void removeAll() {
		views.clear();
		texts.clear();
		textsToRemove.clear();
	}


	/**
	 * Clear the screen.
	 */
	public void clear() {
		g.setColor(Color.black);
		g.fillRect(0, 0, getScreenViewWidth(), getScreenViewHeight());
	}

	/**
	 * Removes all the text entities.
	 */
	public void clearTexts() {
		for (Iterator it = texts.iterator(); it.hasNext();) {
			textsToRemove.add((Text) it.next());
		}
	}


	/**
	 * Get an entity view at given coordinates.
	 *
	 * @param	x		The X world coordinate.
	 * @param	y		The Y world coordinate.
	 *
	 * @return	The entity view, or <code>null</code> if none found.
	 */
	public Entity2DView getEntityViewAt(double x, double y) {
		ListIterator<Entity2DView> it;

		/*
		 * Try the physical entity areas first
		 */
		it = views.listIterator(views.size());

		while (it.hasPrevious()) {
			Entity2DView view = it.previous();

			if (view.getEntity().getArea().contains(x, y)) {
				return view;
			}
		}

		/*
		 * Now the visual entity areas
		 */
		int sx = convertWorldToScreen(x);
		int sy = convertWorldToScreen(y);

		it = views.listIterator(views.size());

		while (it.hasPrevious()) {
			Entity2DView view = it.previous();

			if (view.getArea().contains(sx, sy)) {
				return view;
			}
		}

		return null;
	}


	/**
	 * Get an entity view that is movable at given coordinates.
	 *
	 * @param	x		The X world coordinate.
	 * @param	y		The Y world coordinate.
	 *
	 * @return	The entity view, or <code>null</code> if none found.
	 */
	public Entity2DView getMovableEntityViewAt(final double x, final double y) {
		ListIterator<Entity2DView> it;

		/*
		 * Try the physical entity areas first
		 */
		it = views.listIterator(views.size());

		while (it.hasPrevious()) {
			Entity2DView view = it.previous();

			if(view.isMovable()) {
				if (view.getEntity().getArea().contains(x, y)) {
					return view;
				}
			}
		}

		/*
		 * Now the visual entity areas
		 */
		int sx = convertWorldToScreen(x);
		int sy = convertWorldToScreen(y);

		it = views.listIterator(views.size());

		while (it.hasPrevious()) {
			Entity2DView view = it.previous();

			if(view.isMovable()) {
				if (view.getArea().contains(sx, sy)) {
					return view;
				}
			}
		}

		return null;
	}


	/**
	 * Get the text bubble at specific coordinates.
	 *
	 *
	 *
	 */
	public Text getTextAt(double x, double y) {
		ListIterator<Text> it = texts.listIterator(texts.size());

		int sx = convertWorldToScreen(x);
		int sy = convertWorldToScreen(y);

		while (it.hasPrevious()) {
			Text text = it.previous();

			if (text.getArea().contains(sx, sy)) {
				return text;
			}
		}

		return null;
	}


	/** Translate to world coordinates the given screen coordinate */
	public Point2D translate(Point2D point) {
		double tx = (point.getX() + svx) / SIZE_UNIT_PIXELS;
		double ty = (point.getY() + svy) / SIZE_UNIT_PIXELS;
		return new Point.Double(tx, ty);
	}

	/** Translate to screen coordinates the given world coordinate */
	public Point2D invtranslate(Point2D point) {
		return convertWorldToScreenView(point.getX(), point.getY());
	}


	/**
	 * Convert world X coordinate to screen view coordinate.
	 *
	 * @param	wx		World X coordinate.
	 *
	 * @return	Screen X coordinate (in integer value).
	 */
	public int convertWorldXToScreenView(double wx) {
		return convertWorldToScreen(wx) - svx;
	}


	/**
	 * Convert world Y coordinate to screen view coordinate.
	 *
	 * @param	wy		World Y coordinate.
	 *
	 * @return	Screen Y coordinate (in integer value).
	 */
	public int convertWorldYToScreenView(double wy) {
		return convertWorldToScreen(wy) - svy;
	}


	/**
	 * Convert world coordinates to screen view coordinates.
	 *
	 * This does have some theorical range limits. Assuming a tile size
	 * of 256x256 pixels (very high def), world coordinates are limited
	 * to a little over +/-8 million, before the int (31-bit) values
	 * returned from this are wrapped. So I see no issues, even if
	 * absolute world coordinates are used.
	 *
	 * @param	wx		World X coordinate.
	 * @param	wy		World Y coordinate.
	 *
	 * @return	Screen view coordinates (in integer values).
	 */
	public Point convertWorldToScreenView(double wx, double wy) {
		return new Point(
			convertWorldXToScreenView(wx),
			convertWorldYToScreenView(wy));
	}


	/**
	 * Convert world coordinates to screen coordinates.
	 *
	 * @param	wrect		World area.
	 *
	 * @return	Screen rectangle (in integer values).
	 */
	public Rectangle convertWorldToScreenView(Rectangle2D wrect) {
		return convertWorldToScreenView(wrect.getX(), wrect.getY(), wrect.getWidth(), wrect.getHeight());
	}


	/**
	 * Convert world coordinates to screen coordinates.
	 *
	 * @param	wx		World X coordinate.
	 * @param	wy		World Y coordinate.
	 * @param	wwidth		World area width.
	 * @param	wheight		World area height.
	 *
	 * @return	Screen rectangle (in integer values).
	 */
	public Rectangle convertWorldToScreenView(double wx, double wy, double wwidth, double wheight) {
		return new Rectangle(
			convertWorldXToScreenView(wx),
			convertWorldYToScreenView(wy),
			(int) (wwidth * SIZE_UNIT_PIXELS),
			(int) (wheight * SIZE_UNIT_PIXELS));
	}


	/**
	 * Determine if an area is in the screen view.
	 *
	 * @param	srect		Screen area.
	 *
	 * @return	<code>true</code> if some part of area in in the
	 *		visible screen, otherwise <code>false</code>.
	 */
	public boolean isInScreen(Rectangle srect) {
		return isInScreen(srect.x, srect.y, srect.width, srect.height);
	}


	/**
	 * Determine if an area is in the screen view.
	 *
	 * @param	sx		Screen X coordinate.
	 * @param	sy		Screen Y coordinate.
	 * @param	swidth		Screen area width.
	 * @param	sheight		Screen area height.
	 *
	 * @return	<code>true</code> if some part of area in in the
	 *		visible screen, otherwise <code>false</code>.
	 */
	public boolean isInScreen(int sx, int sy, int swidth, int sheight) {
		return (((sx >= -swidth) && (sx < sw)) && ((sy >= -sheight) && (sy < sh)));
	}


	/** Draw a sprite in screen given its world coordinates */
	public void draw(Sprite sprite, double wx, double wy) {
		Point p = convertWorldToScreenView(wx, wy);

		if (sprite != null) {
			int spritew = sprite.getWidth() + 2;
			int spriteh = sprite.getHeight() + 2;

			if (((p.x >= -spritew) && (p.x < sw)) && ((p.y >= -spriteh) && (p.y < sh))) {
				sprite.draw(g, p.x, p.y);
			}
		}
	}

	public void drawInScreen(Sprite sprite, int sx, int sy) {
		sprite.draw(g, sx, sy);
	}


	/**
	 * Create a sprite representation of some text.
	 *
	 * @param	text		The text.
	 * @param	type		The type.
	 *
	 * @return	A sprite.
	 */
	public Sprite createString(final String text, final NotificationType type) {
		return createString(text, getNotificationColor(type));
	}


	/**
	 * Create a sprite representation of some text.
	 *
	 * @param	text		The text.
	 * @param	color		The text color.
	 *
	 * @return	A sprite.
	 */
	public Sprite createString(String text, Color textColor) {
		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
		        .getDefaultConfiguration();
		Image image = gc.createCompatibleImage(g.getFontMetrics().stringWidth(text) + 2, 16, Transparency.BITMASK);
		Graphics g2d = image.getGraphics();

		drawOutlineString(g2d, textColor, text, 1, 10);

		return new ImageSprite(image);
	}


	/**
	 * Draw a text string (like <em>Graphics</em><code>.drawString()</code>)
	 * only with an outline border. The area drawn extends 1 pixel out
	 * on all side from what would normal be drawn by drawString().
	 *
	 * @param	g		The graphics context.
	 * @param	textColor	The text color.
	 * @param	outlineColor	The outline color.
	 * @param	text		The text to draw.
	 * @param	x		The X position.
	 * @param	y		The Y position.
	 */
	public void drawOutlineString(final Graphics g, final Color textColor, final String text, final int x, final int y) {
		/*
		 * Use light gray as outline for colors < 25% bright.
		 * Luminance = 0.299R + 0.587G + 0.114B
		 */
		int lum = ((textColor.getRed() * 299)
			+ (textColor.getGreen() * 587)
			+ (textColor.getBlue() * 114)) / 1000;

		drawOutlineString(g, textColor, (lum >= 64) ? Color.black : Color.lightGray, text, x, y);
	}


	/**
	 * Draw a text string (like <em>Graphics</em><code>.drawString()</code>)
	 * only with an outline border. The area drawn extends 1 pixel out
	 * on all side from what would normal be drawn by drawString().
	 *
	 * @param	g		The graphics context.
	 * @param	textColor	The text color.
	 * @param	outlineColor	The outline color.
	 * @param	text		The text to draw.
	 * @param	x		The X position.
	 * @param	y		The Y position.
	 */
	public void drawOutlineString(final Graphics g, final Color textColor, final Color outlineColor, final String text, final int x, final int y) {
		g.setColor(outlineColor);
		g.drawString(text, x - 1, y - 1);
		g.drawString(text, x - 1, y + 1);
		g.drawString(text, x + 1, y - 1);
		g.drawString(text, x + 1, y + 1);

		g.setColor(textColor);
		g.drawString(text, x, y);
	}


	private int positionStringOfSize(String text, int width) {
		String[] words = text.split(" ");

		int i = 1;
		// Bugfix: Prevent NPE for empty text intensifly@gmx.com
		String textUnderWidth = "";
		if (words != null) {
			textUnderWidth = words[0];
		}

		while ((i < words.length) && (g.getFontMetrics().stringWidth(textUnderWidth + " " + words[i]) < width)) {
			textUnderWidth = textUnderWidth + " " + words[i];
			i++;
		}

		if ((textUnderWidth.length() == 0) && (words.length > 1)) {
			textUnderWidth = words[1];
		}

		if (g.getFontMetrics().stringWidth(textUnderWidth) > width) {
			return (int) ((float) width / (float) g.getFontMetrics().stringWidth(textUnderWidth) * textUnderWidth
			        .length());
		}

		return textUnderWidth.length();
	}

	// Added support formatted text displaying #keywords in another color
	// intensifly@gmx.com
	// ToDo: optimize the alghorithm, it's a little long ;)

	/**
	 * Formats a text by changing the  color of words starting with {@link #clone()}.S
	 *
	 * @param line the text
	 * @param fontNormal the font
	 * @param colorNormal normal color (for non-special text)
	 */
	public AttributedString formatLine(String line, Font fontNormal, Color colorNormal) {
		Font specialFont = fontNormal.deriveFont(Font.ITALIC);

		// tokenize the string
		List<String> list = Arrays.asList(line.split(" "));

		// recreate the string without the # characters
		StringBuilder temp = new StringBuilder();
		for (String tok : list) {
			if (tok.startsWith("#")) {
				tok = tok.substring(1);
			}
			temp.append(tok + " ");
		}

		// create the attribute string with the formatation
		AttributedString aStyledText = new AttributedString(temp.toString());
		int s = 0;
		for (String tok : list) {
			Font font = fontNormal;
			Color color = colorNormal;
			if (tok.startsWith("##")) {
				tok = tok.substring(1);
			} else if (tok.startsWith("#")) {
				tok = tok.substring(1);
				font = specialFont;
				color = Color.blue;
			}
			if (tok.length() > 0) {
				aStyledText.addAttribute(TextAttribute.FONT, font, s, s + tok.length() + 1);
				aStyledText.addAttribute(TextAttribute.FOREGROUND, color, s, s + tok.length() + 1);
			}
			s += tok.length() + 1;
		}

		return (aStyledText);

	}

	public Sprite createTextBox(String text, int width, Color textColor, Color fillColor, boolean isTalking) {
		java.util.List<String> lines = new java.util.LinkedList<String>();

		int i = 0;
		// Added support for speech balloons. If drawn, they take 10 pixels from
		// the left. intensifly@gmx.com

		int delta = 0;

		if (fillColor != null) {
			delta = 10;
		}
		text = text.trim();
		while (text.length() > 0) {
			int pos = positionStringOfSize(text, width - delta);


			/*
			 * Hard line breaks
			 */
			int nlpos = text.indexOf('\n', 1);
			if ((nlpos  != -1) && (nlpos < pos)) {
				pos = nlpos;
			}

			lines.add(text.substring(0, pos).trim());
			text = text.substring(pos);
			i++;
		}

		int numLines = lines.size();
		int lineLengthPixels = 0;

		for (String line : lines) {
			int lineWidth = g.getFontMetrics().stringWidth(line);
			if (lineWidth > lineLengthPixels) {
				lineLengthPixels = lineWidth;
			}
		}

		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
		        .getDefaultConfiguration();

		int imageWidth = ((lineLengthPixels + delta < width) ? lineLengthPixels + delta : width) + 4;
		int imageHeight = 16 * numLines;

		// Workaround for X-Windows not supporting images of height 0 pixel.
		if (imageHeight == 0) {
			imageHeight = 1;
			logger.warn("Created textbox for empty text");
		}

		Image image = gc.createCompatibleImage(imageWidth, imageHeight, Transparency.BITMASK);

		Graphics2D g2d = (Graphics2D) image.getGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (fillColor != null) {
			Composite xac = g2d.getComposite();
			AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f);
			g2d.setComposite(ac);
			g2d.setColor(fillColor);
			g2d.fillRoundRect(10, 0, ((lineLengthPixels < width) ? lineLengthPixels : width) + 3, 16 * numLines - 1, 4,
			        4);
			g2d.setColor(textColor);
			if (isTalking) {
				g2d.drawRoundRect(10, 0, ((lineLengthPixels < width) ? lineLengthPixels : width) + 3,
				        16 * numLines - 1, 4, 4);
			} else {
				float[] dash = { 4, 2 };
				BasicStroke newStroke = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1, dash, 0);
				Stroke oldStroke = g2d.getStroke();
				g2d.setStroke(newStroke);
				g2d.drawRect(10, 0, ((lineLengthPixels < width) ? lineLengthPixels : width) + 3, 16 * numLines - 1);
				g2d.setStroke(oldStroke);
			}
			g2d.setComposite(xac);
			if (isTalking) {
				g2d.setColor(fillColor);
				Polygon p = new Polygon();
				p.addPoint(10, 3);
				p.addPoint(0, 16);
				p.addPoint(11, 12);
				g2d.fillPolygon(p);
				g2d.setColor(textColor);
				p.addPoint(0, 16);
				g2d.drawPolygon(p);
			}
		}

		i = 0;
		for (String line : lines) {
			AttributedString aStyledText = formatLine(line, g2d.getFont(), textColor);

			if (fillColor == null) {
				g2d.setColor(Color.black);
				g2d.drawString(aStyledText.getIterator(), 1, 2 + i * 16 + 9);
				g2d.drawString(aStyledText.getIterator(), 1, 2 + i * 16 + 11);
				g2d.drawString(aStyledText.getIterator(), 3, 2 + i * 16 + 9);
				g2d.drawString(aStyledText.getIterator(), 3, 2 + i * 16 + 11);
			}

			g2d.setColor(textColor);

			g2d.drawString(aStyledText.getIterator(), 2 + delta, 2 + i * 16 + 10);
			i++;
		}

		return new ImageSprite(image);
	}


	//
	// <GameScreen2D>
	//

	/**
	 * Convert a world unit value to a screen unit value.
	 *
	 * @param	w		World value.
	 *
	 * @return	A screen value (in pixels).
	 */
	public int convertWorldToScreen(double w) {
		return (int) (w * SIZE_UNIT_PIXELS);
	}

	/**
	 * Get the full screen height in pixels.
	 *
	 * @return	The height.
	 */
	public int getScreenHeight() {
		return convertWorldToScreen(wh);
	}

	/**
	 * Get the full screen width in pixels.
	 *
	 * @return	The width.
	 */
	public int getScreenWidth() {
		return convertWorldToScreen(ww);
	}

	/**
	 * Get the view height in pixels.
	 *
	 * @return	The view height.
	 */
	public int getScreenViewHeight() {
		return sh;
	}

	/**
	 * Get the view width in pixels.
	 *
	 * @return	The view width.
	 */
	public int getScreenViewWidth() {
		return sw;
	}

	/**
	 * Get the view X screen coordinate.
	 *
	 * @return	The X coordinate of the left side.
	 */
	public int getScreenViewX() {
		return svx;
	}

	/**
	 * Get the view Y screen coordinate.
	 *
	 * @return	The Y coordinate of the left side.
	 */
	public int getScreenViewY() {
		return svy;
	}

	//
	//

	public static class EntityViewComparator implements Comparator<Entity2DView> {
		//
		// Comparator
		//

		public int compare(Entity2DView view1, Entity2DView view2) {
			int	rv;


			rv = view1.getZIndex() - view2.getZIndex();

			if(rv == 0) {
				Rectangle area1 = view1.getArea();
				Rectangle area2 = view2.getArea();

				rv = (area1.y + area1.height) - (area2.y + area2.height);

				if(rv == 0) {
					rv = area1.x - area2.x;
				}
			}

			return rv;
		}
	}
}
