package adf.sample.centralized;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.centralized.CommandScout;
import adf.agent.communication.standard.bundle.centralized.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.centralized.CommandExecutor;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.Area;
import rescuecore2.worldmodel.AbstractEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class CommandExecutorScoutPolice extends CommandExecutor<CommandScout> {

    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_SCOUT = 1;

    private PathPlanning pathPlanning;

    private ExtAction actionExtClear;

    private int commandType;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    public CommandExecutorScoutPolice(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.commandType = ACTION_UNKNOWN;
        this.scoutTargets = new HashSet<>();
        this.commanderID = null;

        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("CommandExecutorScoutPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionExtClear = moduleManager.getExtAction("CommandExecutorScoutPolice.ActionExtClear", "adf.sample.extaction.ActionExtClear");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("CommandExecutorScoutPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionExtClear = moduleManager.getExtAction("CommandExecutorScoutPolice.ActionExtClear", "adf.sample.extaction.ActionExtClear");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("CommandExecutorScoutPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionExtClear = moduleManager.getExtAction("CommandExecutorScoutPolice.ActionExtClear", "adf.sample.extaction.ActionExtClear");
                break;
        }
    }

    @Override
    public CommandExecutor<CommandScout> setCommand(CommandScout command) {
        EntityID agentID = this.agentInfo.getID();
        if(command.isToIDDefined() && (Objects.requireNonNull(command.getToID()).getValue() == agentID.getValue())) {
            EntityID target = command.getTargetID();
            if(target == null) {
                target = this.agentInfo.getPosition();
            }
            this.commandType = ACTION_SCOUT;
            this.commanderID = command.getSenderID();
            this.scoutTargets = new HashSet<>();
            this.scoutTargets.addAll(
                    worldInfo.getObjectsInRange(target, command.getRange())
                            .stream()
                            .filter(e -> e instanceof Area && e.getStandardURN() != REFUGE)
                            .map(AbstractEntity::getID)
                            .collect(Collectors.toList())
            );
        }
        return this;
    }

    public CommandExecutor precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.actionExtClear.precompute(precomputeData);
        return this;
    }

    public CommandExecutor resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        this.actionExtClear.resume(precomputeData);
        return this;
    }

    public CommandExecutor preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        this.actionExtClear.preparate();
        return this;
    }

    public CommandExecutor updateInfo(MessageManager messageManager){
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.actionExtClear.updateInfo(messageManager);

        if(this.isCommandCompleted()) {
            if(this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
                this.commandType = ACTION_UNKNOWN;
                this.scoutTargets = new HashSet<>();
                this.commanderID = null;
            }
        }
        return this;
    }

    @Override
    public CommandExecutor calc() {
        this.result = null;
        EntityID position = this.agentInfo.getPosition();
        if(this.commandType == ACTION_SCOUT) {
            if (this.scoutTargets == null || this.scoutTargets.isEmpty()) {
                return this;
            }
            this.pathPlanning.setFrom(position);
            this.pathPlanning.setDestination(this.scoutTargets);
            List<EntityID> path = this.pathPlanning.calc().getResult();
            if (path != null) {
                EntityID target = path.size() > 0 ? path.get(path.size() - 1) : position;
                Action action = this.actionExtClear.setTarget(target).calc().getAction();
                if (action == null) {
                    action = new ActionMove(path);
                }
                this.result = action;
            }
        }
        return this;
    }

    private boolean isCommandCompleted() {
        if(this.commandType == ACTION_SCOUT) {
            if(this.scoutTargets != null) {
                this.scoutTargets.removeAll(this.worldInfo.getChanged().getChangedEntities());
            }
            return (this.scoutTargets == null || this.scoutTargets.isEmpty());
        }
        return true;
    }
}
