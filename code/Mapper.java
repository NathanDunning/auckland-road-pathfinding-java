import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.*;

/**
 * This is the main class for the mapping program. It extends the GUI abstract
 * class and implements all the methods necessary, as well as having a main
 * function.
 *
 * @author tony
 */
public class Mapper extends GUI {
	public static final Color NODE_COLOUR = new Color(77, 113, 255);
	public static final Color SEGMENT_COLOUR = new Color(130, 130, 130);
	public static final Color HIGHLIGHT_COLOUR = new Color(255, 219, 77);

	// these two constants define the size of the node squares at different zoom
	// levels; the equation used is node size = NODE_INTERCEPT + NODE_GRADIENT *
	// log(scale)
	public static final int NODE_INTERCEPT = 1;
	public static final double NODE_GRADIENT = 0.8;

	// defines how much you move per button press, and is dependent on scale.
	public static final double MOVE_AMOUNT = 100;
	// defines how much you zoom in/out per button press, and the maximum and
	// minimum zoom levels.
	public static final double ZOOM_FACTOR = 1.3;
	public static final double MIN_ZOOM = 1, MAX_ZOOM = 200;

	// how far away from a node you can click before it isn't counted.
	public static final double MAX_CLICKED_DISTANCE = 0.15;

	// Different clicks for A* Search
	private boolean click2 = false;
	Node firstNode, secondNode;

	// these two define the 'view' of the program, ie. where you're looking and

	// how zoomed in you are.
	private Location origin;
	private double scale;

	// our data structures.
	private Graph graph;
	private Trie trie;

	@Override
	protected void redraw(Graphics g) {
		if (graph != null)
			graph.draw(g, getDrawingAreaDimension(), origin, scale);
	}

	@Override
	protected void onClick(MouseEvent e) {
		Location clicked = Location.newFromPoint(e.getPoint(), origin, scale);

		// find the closest node.
		double bestDist = Double.MAX_VALUE;
		for (Node node : graph.nodes.values()) {
			double distance = clicked.distance(node.location);
			if (distance < bestDist) {
				bestDist = distance;
				if (click2 == false) {
					firstNode = node;
				} else {
					secondNode = node;
				}
			}
		}

		// If it is their first mouse click work with firstNode
		if (!click2) {
			// Highlight firstNode and show info
			if (clicked.distance(firstNode.location) < MAX_CLICKED_DISTANCE) {
				// Remove highlight of previous segments
				graph.setHighlightSegs(null);
				// Highlight node
				graph.setHighlight(firstNode);
				getTextOutputArea().setText(firstNode.toString());
				click2 = true;
				return;
			}
		}
		// If it is the second click use secondNode
		else {
			// Highlight firstNode and show info
			if (clicked.distance(secondNode.location) < MAX_CLICKED_DISTANCE) {
				// Highlight node
				graph.clearHighlight();
				getTextOutputArea().setText(secondNode.toString());
			}
			// Call the aStarSearch and highlight the segments
			if (!firstNode.equals(secondNode)) {
				Set<Segment> shortest = aStarSearch(firstNode, secondNode);
				graph.setHighlightSegs(shortest);
				// Print out the road names
				for (Segment s : shortest) {
					getTextOutputArea().setText(s.road.name);
				}
			}
			// Reset the nodes and click boolean back to use for the next set of clicks
			firstNode = secondNode = null;
			click2 = false;
		}
	}

	@Override
	protected void onSearch() {
		if (trie == null)
			return;

		// get the search query and run it through the trie.
		String query = getSearchBox().getText();
		Collection<Road> selected = trie.get(query);

		// figure out if any of our selected roads exactly matches the search
		// query. if so, as per the specification, we should only highlight
		// exact matches. there may be (and are) many exact matches, however, so
		// we have to do this carefully.
		boolean exactMatch = false;
		for (Road road : selected)
			if (road.name.equals(query))
				exactMatch = true;

		// make a set of all the roads that match exactly, and make this our new
		// selected set.
		if (exactMatch) {
			Collection<Road> exactMatches = new HashSet<>();
			for (Road road : selected)
				if (road.name.equals(query))
					exactMatches.add(road);
			selected = exactMatches;
		}

		// set the highlighted roads.
		graph.setHighlight(selected);

		// now build the string for display. we filter out duplicates by putting
		// it through a set first, and then combine it.
		Collection<String> names = new HashSet<>();
		for (Road road : selected)
			names.add(road.name);
		String str = "";
		for (String name : names)
			str += name + "; ";

		if (str.length() != 0)
			str = str.substring(0, str.length() - 2);
		getTextOutputArea().setText(str);
	}

	@Override
	protected void onMove(Move m) {
		if (m == GUI.Move.NORTH) {
			origin = origin.moveBy(0, MOVE_AMOUNT / scale);
		} else if (m == GUI.Move.SOUTH) {
			origin = origin.moveBy(0, -MOVE_AMOUNT / scale);
		} else if (m == GUI.Move.EAST) {
			origin = origin.moveBy(MOVE_AMOUNT / scale, 0);
		} else if (m == GUI.Move.WEST) {
			origin = origin.moveBy(-MOVE_AMOUNT / scale, 0);
		} else if (m == GUI.Move.ZOOM_IN) {
			if (scale < MAX_ZOOM) {
				// yes, this does allow you to go slightly over/under the
				// max/min scale, but it means that we always zoom exactly to
				// the centre.
				scaleOrigin(true);
				scale *= ZOOM_FACTOR;
			}
		} else if (m == GUI.Move.ZOOM_OUT) {
			if (scale > MIN_ZOOM) {
				scaleOrigin(false);
				scale /= ZOOM_FACTOR;
			}
		}
	}

	@Override
	protected void onLoad(File nodes, File roads, File segments, File polygons) {
		graph = new Graph(nodes, roads, segments, polygons);
		trie = new Trie(graph.roads.values());
		origin = new Location(-250, 250); // close enough
		scale = 1;
	}

	/**
	 * This method does the nasty logic of making sure we always zoom into/out of
	 * the centre of the screen. It assumes that scale has just been updated to be
	 * either scale * ZOOM_FACTOR (zooming in) or scale / ZOOM_FACTOR (zooming out).
	 * The passed boolean should correspond to this, ie. be true if the scale was
	 * just increased.
	 */
	private void scaleOrigin(boolean zoomIn) {
		Dimension area = getDrawingAreaDimension();
		double zoom = zoomIn ? 1 / ZOOM_FACTOR : ZOOM_FACTOR;

		int dx = (int) ((area.width - (area.width * zoom)) / 2);
		int dy = (int) ((area.height - (area.height * zoom)) / 2);

		origin = Location.newFromPoint(new Point(dx, dy), origin, scale);
	}

	public Set<Segment> aStarSearch(Node from, Node to) {
		double heuristicCost = from.location.distance(to.location);
		Set<Node> visitedNodes = new HashSet<Node>();
		// Creating a priority queue and adding the first element
		PriorityQueue<AStarNode> fringe = new PriorityQueue<AStarNode>(
				(a, b) -> ((int) (a.getMinCostToNext() - b.getMinCostToNext())));
		fringe.offer(new AStarNode(from, null, 0, heuristicCost));
		AStarNode currentNode = null;
		// Iterating through the while loop
		while (!fringe.isEmpty()) {
			currentNode = fringe.poll();
			Node current = currentNode.getCurrent();
			Node previous = currentNode.getPrevious();
			double g = currentNode.getCostFromStart();
			double f = currentNode.getMinCostToNext();
			if (!current.isVisited) {
				// Set current node to visited and set its previous to the previous
				current.setVisited();
				visitedNodes.add(current);
				current.setPrevious(previous);
				if (currentNode.getCurrent().equals(to)) {
					break;
				}
				// Iterate through current node to find the length of edge and children nodes
				for (Segment s : current.segments) {
					// Finding the start and the end (assigning them correctly)
					Node start, end;
					double length = s.length;
					if (s.end.equals(current)) {
						start = s.end;
						end = s.start;
					} else {
						start = s.start;
						end = s.end;
					}

					// Checking that our children (end nodes) are unvisited, finding new 'g' and 'f'
					if (!end.isVisited) {
						double gNew = g + length;
						double fNew = gNew + end.location.distance(to.location);
						fringe.offer(new AStarNode(end, start, gNew, fNew));
					}
				}
			}
		}
		// Adding all the previous to a list
		HashSet<Segment> shortest = new HashSet<Segment>();
		for (Node backtrack = currentNode.getCurrent(); backtrack.previous != null; backtrack = backtrack.previous) {
			for (Segment s : backtrack.segments) {
				//Add our segments into our set so we can highlight them
				if (s.start.equals(backtrack) && (s.end.equals(backtrack.previous))) {
					shortest.add(s);
				} else if (s.start.equals(backtrack.previous) && (s.end.equals(backtrack))) {
					shortest.add(s);
				}
			}
		}
		//Set all of our visited nodes to unvisited for the next set of clicks
		for(Node n : visitedNodes) {
			n.isVisited = false;
		}

		return shortest;
	}

	public static void main(String[] args) {
		new Mapper();
	}
}

// code for COMP261 assignments