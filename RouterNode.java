import javax.swing.*;        
import java.util.Arrays;

public class RouterNode {
    private int myID;
    private GuiTextArea myGUI;
    private RouterSimulator sim;

    private int[] costs = new int[RouterSimulator.NUM_NODES];
    private int[][] distanceVector = new int[RouterSimulator.NUM_NODES][RouterSimulator.NUM_NODES];
    private int[] minRoute = new int[RouterSimulator.NUM_NODES];
    private boolean pR = true;

    //--------------------------------------------------
    public RouterNode(int ID, RouterSimulator sim, int[] costs) {
	myID = ID;
	this.sim = sim;
	myGUI =new GuiTextArea("  Output window for Router #"+ ID + "  ");
	System.arraycopy(costs, 0, this.costs, 0, RouterSimulator.NUM_NODES);

	init_distanceVector();
	
	/* Set node's DV to the direct link costs */
	System.arraycopy(costs, 0, distanceVector[myID], 0, RouterSimulator.NUM_NODES);
	
	/* Initially send the cost vector to all neighbouring nodes */
	for ( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		if ( costs[i] == 1  )
		    sendUpdate(new RouterPacket (myID,i,costs) );
	    }
    }

    //--------------------------------------------------
    public void recvUpdate(RouterPacket pkt) {
	
	/* copy the new mincost vector to the distance table */
	System.arraycopy(pkt.mincost,0,distanceVector[pkt.sourceid],0,RouterSimulator.NUM_NODES);

	/* update new min-paths in the distance table */
	updateDistancevector();
    }
  

    //--------------------------------------------------
    private void sendUpdate(RouterPacket pkt) {
	sim.toLayer2(pkt);

    }
  

    //--------------------------------------------------
    public void printDistanceTable() {

	myGUI.println("Current table for " + myID +
		      "  at time " + sim.getClocktime());
	myGUI.println("Distancevector:");
	
	/* Found this StringBuilder thing that's pretty neat //A */
	StringBuilder s = new StringBuilder(F.format("dist", 7) + " | ");
	for(int i = 0; i < RouterSimulator.NUM_NODES; ++i)
	    s.append(F.format(i, 5));
	myGUI.println(s.toString());

	for(int i = 0; i < s.length(); ++i)	    
	    myGUI.print("-");
	myGUI.println();
	
	/* Print distance to neighbours */
	for(int orig = 0; orig < RouterSimulator.NUM_NODES; ++orig)
	    {
		if(orig == myID)
		    continue;
		s = new StringBuilder("nbr" + F.format(orig, 3) + " | ");
		for(int i = 0; i < RouterSimulator.NUM_NODES; ++i)
		    s.append(F.format(distanceVector[orig][i], 5));
		myGUI.println(s.toString());
	    }
    }

    //--------------------------------------------------
    public void updateLinkCost(int dest, int newcost) {
	costs[dest] = newcost;
	updateDistancevector();
    }

    /* Init DV. Set all entries to INFINITY */
    private void init_distanceVector()
    {
	for(int[] row : distanceVector)
	    Arrays.fill(row, RouterSimulator.INFINITY);
    }


    /* Find the min-costs paths for every pair (source,dest) in the distance table */    
    private void updateDistancevector()
    {

	for ( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		for ( int j = 0; j < RouterSimulator.NUM_NODES; ++j)
		    find_min_path(i,j);
	    }
    }
    
    private void find_min_path(int source, int dest)
    {
	for ( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		int distance = distanceVector[i][dest] + distanceVector[source][i];
		if (distance >= RouterSimulator.INFINITY )
		    continue;
		else if ( distance < distanceVector[source][dest])  
		    distanceVector[source][dest] = distance;
	    }
    }
}
