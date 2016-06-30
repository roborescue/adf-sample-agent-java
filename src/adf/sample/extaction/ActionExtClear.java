package adf.sample.extaction;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.police.ActionClear;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import adf.sample.SampleModuleKey;
import com.infomatiq.jsi.Point;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class ActionExtClear extends ExtAction {

    private int distance;
    private EntityID target;

    private Map<EntityID, Set<Point2D>> pointCache;

    public ActionExtClear(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
        this.distance = this.scenarioInfo.getClearRepairDistance();
        this.pointCache = new HashMap<>();
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        if(targets != null) {
            this.target = targets[0];
        }
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        if(this.target == null) {
            return this;
        }
        Human me = (Human)this.agentInfo.me();
        int agentX = me.getX();
        int agentY = me.getY();
        Blockade blockade = null;
        Road road;
        StandardEntity entity = this.worldInfo.getEntity(this.target);
        if(entity.getStandardURN().equals(StandardEntityURN.BLOCKADE)) {
            blockade = (Blockade) entity;
            if(!blockade.isPositionDefined()) { return this; }
            road = (Road) this.worldInfo.getEntity(blockade.getPosition());
            this.result = this.getClearAction(agentX, agentY, road, blockade);
            return this;
        }
        else if(entity.getStandardURN().equals(StandardEntityURN.ROAD) || entity.getStandardURN().equals(StandardEntityURN.HYDRANT)) {
            road = (Road)entity;
            this.result = this.moveImpassableArea(me, road);
            // check need to move
            // result != null -> move
            // result == null -> clear or check Area node
            if(this.result != null) { return this; }
            // check Blockade on target area
            if(!road.isBlockadesDefined()) { return this; }
            double d = Double.MAX_VALUE;
            for(EntityID id : road.getBlockades()) {
                Blockade b = (Blockade) this.worldInfo.getEntity(id);
                double blockadeDistance = this.worldInfo.getDistance(me, b);
                if(d > blockadeDistance) {
                    d = blockadeDistance;
                    blockade = b;
                }
            }
            if(blockade == null) { return this; }
            // check distance
            // move to blockade
            if(this.getDistance(me.getX(), me.getY(), blockade.getX(), blockade.getY()) > this.distance) {
                PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
                List<EntityID> path = pathPlanning.setFrom(me.getPosition()).setDestination(blockade.getPosition()).calc().getResult();
                if (path != null && path.size() > 0) {
                    this.result = new ActionMove(path, blockade.getX(), blockade.getY());
                    return this;
                }
            }
            // run clear action
            this.result = this.getClearAction(agentX, agentY, road, blockade);
        }
        return this;
    }

    private Action getClearAction(int agentX, int agentY, Road road, Blockade blockade) {
        if(road == null || blockade == null) {return null;}

        List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(blockade.getApexes()), true);
        double best = Double.MAX_VALUE;
        Point2D bestPoint = null;
        Point2D origin = new Point2D(agentX, agentY);
        for (Line2D next : lines) {
            Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
            double d = GeometryTools2D.getDistance(origin, closest);
            if (d < best) {
                best = d;
                bestPoint = closest;
            }
        }
        if(bestPoint != null) {
            Vector2D v = bestPoint.minus(new Point2D(agentX, agentY));
            v = v.normalised().scale(1000000);
            return new ActionClear((int) (agentX + v.getX()), (int) (agentY + v.getY()));
        }
        else {
            PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
            List<EntityID> path = pathPlanning.setFrom(this.agentInfo.getPosition()).setDestination(road.getID()).calc().getResult();
            if(path != null && path.size() > 0) {
                return new ActionMove(path, blockade.getX(), blockade.getY());
            }
        }
        return null;
    }


    private Action moveImpassableArea(Human me, Area area) {
        PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
        if (this.worldInfo.getDistance(me, area) > this.distance) {
            List<EntityID> path = pathPlanning.setFrom(me.getPosition()).setDestination(area.getID()).calc().getResult();
            // fix
            if (path != null && path.size() > 0) {
                return new ActionMove(path);
            }
        }
        /*int[] apexList = area.getApexList();
        double meX = me.getX();
        double meY = me.getY();
        Set<Point2D> pointSet = this.pointCache.get(area.getID());
        if (pointSet == null) {
            pointSet = new HashSet<>();
            for (int i = 0; i < apexList.length; i += 2) {
                pointSet.add(new Point2D(apexList[i], apexList[i + 1]));
            }
        }
        Set<Point2D> removeSet = new HashSet<>();
        for(Point2D point : pointSet) {
            if(this.getDistance(meX, meY, point.getX(), point.getY()) < this.distance) {
                removeSet.add(point);
            }
        }
        pointSet.removeAll(removeSet);
        if(pointSet.size() > 0) {
            for(Point2D point : pointSet) {
                List<EntityID> path = pathPlanning.setFrom(me.getPosition()).setDestination(area.getID()).calc().getResult();
                if (path != null && path.size() > 0) {
                    this.pointCache.put(area.getID(), pointSet);
                    return new ActionMove(path, (int) point.getX(), (int) point.getX());
                }
            }
        }
        this.pointCache.put(area.getID(), pointSet);*/
        return null;
    }

    /*private Action moveImpassableArea(Human me, Area area) {
        PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
        if(this.worldInfo.getDistance(me, area) > this.distance) {
            List<EntityID> path = pathPlanning.setFrom(me.getPosition()).setDestination(area.getID()).calc().getResult();
            // fix
            if(path != null && path.size() > 0) {
                return new ActionMove(path);
            }
        }
        int[] apexList = area.getApexList();
        double meX = me.getX();
        double meY = me.getY();
        Set<Point2D> pointSet = this.pointCache.get(area.getID());
        if(pointSet == null) {
            pointSet = new HashSet<>();
        }
        for(int i = 0; i < apexList.length; i += 2) {
            double x = apexList[i];
            double y = apexList[i+1];
            Point2D point = new Point2D(x, y);
            if(this.getDistance(meX, meY, x, y) > this.distance) {
                if(!this.contains(pointSet, point, 2000.0)) {
                    List<EntityID> path = pathPlanning.setFrom(me.getPosition()).setDestination(area.getID()).calc().getResult();
                    if (path != null && path.size() > 0) {
                        this.pointCache.put(area.getID(), pointSet);
                        return new ActionMove(path, (int) x, (int) y);
                    }
                }
            } else {
                pointSet.add(point);
            }
        }
        this.pointCache.put(area.getID(), pointSet);
        return null;
    }*/

    private double getDistance(double fromX, double fromY, double toX, double toY) {
        double dx = fromX - toX;
        double dy = fromY - toY;
        return Math.hypot(dx, dy);
    }

    private boolean equals(Point2D point1, Point2D point2, double e) {
        return (point1.getX() - e) <= point2.getX() && (point1.getX() + e) >= point2.getX() && (point1.getY() - e) <= point2.getY() && (point1.getY() + e) >= point2.getY();
    }

    /*private boolean contains(Set<Point2D> pointSet, Point2D point, double e) {
        for(Point2D p : pointSet) {
            if(() {
                return true;
            }
        }
        return false;
    }*/

}
