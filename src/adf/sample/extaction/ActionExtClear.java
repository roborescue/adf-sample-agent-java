package adf.sample.extaction;

import adf.agent.action.common.ActionMove;
import adf.agent.action.police.ActionClear;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.sample.SampleModuleKey;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import com.infomatiq.jsi.Rectangle;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

public class ActionExtClear extends ExtAction {

    private int distance;
    private EntityID target;

    public ActionExtClear(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
        this.distance = this.scenarioInfo.getClearRepairDistance();
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
        Blockade blockade;
        Road road;
        StandardEntity entity = this.worldInfo.getEntity(this.target);
        if(entity.getStandardURN().equals(StandardEntityURN.BLOCKADE)) {
            blockade = (Blockade) entity;
            if(!blockade.isPositionDefined()) { return this; }
            road = (Road) this.worldInfo.getEntity(blockade.getPosition());
        }
        else if(entity.getStandardURN().equals(StandardEntityURN.ROAD) || entity.getStandardURN().equals(StandardEntityURN.HYDRANT)) {
            road = (Road)entity;
            if(this.worldInfo.getDistance(me, road) > this.distance) {
                PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
                List<EntityID> path = pathPlanning.setFrom(me.getPosition()).setDestination(this.target).calc().getResult();
                if(path != null && path.size() > 1) {
                    this.result = new ActionMove(path);
                    return this;
                }
                List<EntityID> neighbours = new ArrayList<>();
                for(EntityID id : road.getNeighbours()) {
                    entity = this.worldInfo.getEntity(id);
                    if(entity instanceof Road) {
                        road = (Road) entity;
                        if(this.worldInfo.getDistance(me, road) < this.distance) {
                            neighbours.clear();
                            break;
                        }
                        else {
                            neighbours.add(id);
                        }
                    }
                }
                if(neighbours.size() > 0) {
                    path = pathPlanning.setFrom(me.getPosition()).setDestination(neighbours).calc().getResult();
                    if(path != null && path.size() > 1) { this.result = new ActionMove(path); }
                }
                return this;
            }
            if(!road.isBlockadesDefined()) {
                return this;
            }
            blockade = (Blockade)this.worldInfo.getEntity(road.getBlockades().get(0));
        }
        else {
            return this;
        }

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
            this.result = new ActionClear((int) (agentX + v.getX()), (int) (agentY + v.getY()));
        }
        else {
            PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
            List<EntityID> path = pathPlanning.setFrom(this.agentInfo.getPosition()).setDestination(road.getID()).calc().getResult();
            if(path != null) {
                this.result = new ActionMove(path, blockade.getX(), blockade.getY());
            }
        }
        return this;
    }

}
