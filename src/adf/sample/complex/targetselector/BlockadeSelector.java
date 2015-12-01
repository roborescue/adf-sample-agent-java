package adf.sample.complex.targetselector;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.complex.TargetSelector;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class BlockadeSelector extends TargetSelector<Blockade> {

    private int distance;
    EntityID result;

    public BlockadeSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si) {
        super(ai, wi, si);
        this.distance = si.getClearRepairDistance();
    }

    @Override
    public TargetSelector<Blockade> calc() {
        Area location = this.agentInfo.getLocation();
        Blockade result = getTargetBlockade(location, distance);
        if (result != null) {
            this.result = result.getID();
        }
        for (EntityID next : location.getNeighbours()) {
            location = (Area)this.worldInfo.getEntity(next);
            result = getTargetBlockade(location, distance);
            if (result != null) {
                this.result = result.getID();
            }
        }
        if(this.result == null) {
            this.result = this.getOtherBlockade();
        }
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    private Blockade getTargetBlockade(Area area, int maxDistance) {
        if (area == null || !area.isBlockadesDefined()) {
            return null;
        }
        List<EntityID> ids = area.getBlockades();
        int x = ((Human)this.agentInfo.me()).getX();
        int y = ((Human)this.agentInfo.me()).getY();
        for (EntityID next : ids) {
            Blockade b = (Blockade)this.worldInfo.getEntity(next);
            double d = findDistanceTo(b, x, y);
            if (maxDistance < 0 || d < maxDistance) {
                return b;
            }
        }
        return null;
    }

    private int findDistanceTo(Blockade b, int x, int y) {
        List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
        double best = Double.MAX_VALUE;
        Point2D origin = new Point2D(x, y);
        for (Line2D next : lines) {
            Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
            double d = GeometryTools2D.getDistance(origin, closest);
            if (d < best) {
                best = d;
            }
        }
        return (int)best;
    }

    private EntityID getOtherBlockade() {
        Collection<StandardEntity> e = this.worldInfo.getEntitiesOfType(StandardEntityURN.ROAD, StandardEntityURN.HYDRANT);
        List<Road> result = new ArrayList<>();
        for (StandardEntity next : e) {
            Road r = (Road)next;
            if (r.isBlockadesDefined() && !r.getBlockades().isEmpty()) {
                result.add(r);
            }
        }
        if(result.isEmpty()) {
            return null;
        }
        result.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getLocation()));
        return result.get(0).getBlockades().get(0);
    }
}
