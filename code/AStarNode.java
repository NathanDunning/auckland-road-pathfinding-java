import java.util.Comparator;

public class AStarNode implements Comparable<AStarNode> {

	private Node current;
	private Node previous;
	private double costFromStart;
	private double minCostToDest;

	public AStarNode(Node current, Node previous, double g, double f) {
		this.current = current;
		this.previous = previous;
		this.costFromStart = g;
		this.minCostToDest = f;
	}

	public Node getCurrent() {
		return this.current;
	}

	public Node getPrevious() {
		return this.previous;
	}

	public void setPrevious(Node s) {
		this.previous = s;
	}

	public double getCostFromStart() {
		return this.costFromStart;
	}

	public double getMinCostToNext() {
		return this.minCostToDest;
	}

	@Override
	public int compareTo(AStarNode o) {
		return (int)(this.minCostToDest - o.minCostToDest);
	}

}
