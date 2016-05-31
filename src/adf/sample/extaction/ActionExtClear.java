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
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionExtClear extends ExtAction {

    private EntityID target;

    public ActionExtClear(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
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
        int agentX = ((Human)this.agentInfo.me()).getX();
        int agentY = ((Human)this.agentInfo.me()).getY();
        Blockade blockade;
        Road road;
        StandardEntity entity = this.worldInfo.getEntity(this.target);
        if(entity.getStandardURN().equals(StandardEntityURN.BLOCKADE)) {
            blockade = (Blockade) entity;
            road = (Road) this.worldInfo.getEntity(blockade.getPosition());
        }
        else if(entity.getStandardURN().equals(StandardEntityURN.ROAD) || entity.getStandardURN().equals(StandardEntityURN.HYDRANT)) {
            road = (Road)entity;
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
