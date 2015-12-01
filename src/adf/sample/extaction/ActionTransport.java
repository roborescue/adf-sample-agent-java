package adf.sample.extaction;

import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.info.AgentInfo;
import adf.agent.info.WorldInfo;
import adf.component.algorithm.PathPlanning;
import adf.component.extaction.ExtAction;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionTransport extends ExtAction {

    private WorldInfo worldInfo;
    private AgentInfo agentInfo;
    private PathPlanning pathPlanning;

    private Human target;

    public ActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, PathPlanning pathPlanning, Human target) {
        super();
        this.worldInfo = worldInfo;
        this.agentInfo = agentInfo;
        this.pathPlanning = pathPlanning;
        this.target = target;
    }

    @Override
    public ExtAction calc() {
        this.result = new ActionRest();
        if(this.target.getPosition().equals(this.agentInfo.getID())) {
            if(agentInfo.getLocation().getStandardURN().equals(StandardEntityURN.REFUGE)) {
                this.result = new ActionUnload();
            }
            else {
                this.pathPlanning.setFrom(agentInfo.getPosition());
                this.pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
                List<EntityID> path = this.pathPlanning.getResult();
                if (path != null) {
                    this.result = new ActionMove(path);
                }
            }
        }
        else {
            if (target.getPosition().equals(agentInfo.getPosition())) {
                if ((target instanceof Civilian) && target.getBuriedness() == 0 && !(agentInfo.getLocation() instanceof Refuge)) {
                    this.result = new ActionLoad(target.getID());
                } else if (target.getBuriedness() > 0) {
                    this.result = new ActionRescue(target.getID());
                }
            } else {
                this.pathPlanning.setFrom(agentInfo.getPosition());
                this.pathPlanning.setDestination(target.getPosition());
                List<EntityID> path = this.pathPlanning.getResult();
                if (path != null) {
                    this.result = new ActionMove(path);
                }
            }
        }
        return this;
    }

}
