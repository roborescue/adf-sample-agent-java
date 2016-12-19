package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.action.fire.ActionRefill;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.centralized.CommandFire;
import adf.agent.communication.standard.bundle.centralized.CommandScout;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.centralized.CommandExecutor;
import adf.component.communication.CommunicationMessage;
import adf.component.extaction.ExtAction;
import adf.component.module.complex.BuildingDetector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsFireBrigade;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;
import java.util.Objects;

public class SampleTacticsFireBrigade extends TacticsFireBrigade {
    private BuildingDetector buildingDetector;
    private Search search;

    private ExtAction actionFireFighting;
    private ExtAction actionExtMove;

    private CommandExecutor<CommandFire> commandExecutorFire;
    private CommandExecutor<CommandScout> commandExecutorScout;

    private CommunicationMessage recentCommand;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        this.recentCommand = null;
        // init Algorithm Module & ExtAction
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.search = moduleManager.getModule("TacticsFireBrigade.Search", "adf.sample.module.complex.SampleSearch");
                this.buildingDetector = moduleManager.getModule("TacticsFireBrigade.BuildingDetector", "adf.sample.module.complex.SampleBuildingDetector");
                this.actionFireFighting = moduleManager.getExtAction("TacticsFireBrigade.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("TacticsFireBrigade.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                this.commandExecutorFire = moduleManager.getCommandExecutor("TacticsFireBrigade.CommandExecutorFire", "adf.sample.centralized.CommandExecutorFire");
                this.commandExecutorScout = moduleManager.getCommandExecutor("TacticsFireBrigade.CommandExecutorScout", "adf.sample.centralized.CommandExecutorScout");
                break;
            case PRECOMPUTED:
                this.search = moduleManager.getModule("TacticsFireBrigade.Search", "adf.sample.module.complex.SampleSearch");
                this.buildingDetector = moduleManager.getModule("TacticsFireBrigade.BuildingDetector", "adf.sample.module.complex.SampleBuildingDetector");
                this.actionFireFighting = moduleManager.getExtAction("TacticsFireBrigade.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("TacticsFireBrigade.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                this.commandExecutorFire = moduleManager.getCommandExecutor("TacticsFireBrigade.CommandExecutorFire", "adf.sample.centralized.CommandExecutorFire");
                this.commandExecutorScout = moduleManager.getCommandExecutor("TacticsFireBrigade.CommandExecutorScout", "adf.sample.centralized.CommandExecutorScout");
                break;
            case NON_PRECOMPUTE:
                this.search = moduleManager.getModule("TacticsFireBrigade.Search", "adf.sample.module.complex.SampleSearch");
                this.buildingDetector = moduleManager.getModule("TacticsFireBrigade.BuildingDetector", "adf.sample.module.complex.SampleBuildingDetector");
                this.actionFireFighting = moduleManager.getExtAction("TacticsFireBrigade.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("TacticsFireBrigade.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                this.commandExecutorFire = moduleManager.getCommandExecutor("TacticsFireBrigade.CommandExecutorFire", "adf.sample.centralized.CommandExecutorFire");
                this.commandExecutorScout = moduleManager.getCommandExecutor("TacticsFireBrigade.CommandExecutorScout", "adf.sample.centralized.CommandExecutorScout");
                break;
        }
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.search.precompute(precomputeData);
        this.buildingDetector.precompute(precomputeData);
        this.actionFireFighting.precompute(precomputeData);
        this.actionExtMove.precompute(precomputeData);
        this.commandExecutorFire.precompute(precomputeData);
        this.commandExecutorScout.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.search.resume(precomputeData);
        this.buildingDetector.resume(precomputeData);
        this.actionFireFighting.resume(precomputeData);
        this.actionExtMove.resume(precomputeData);
        this.commandExecutorFire.resume(precomputeData);
        this.commandExecutorScout.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.search.preparate();
        this.buildingDetector.preparate();
        this.actionFireFighting.preparate();
        this.actionExtMove.preparate();
        this.commandExecutorFire.preparate();
        this.commandExecutorScout.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.buildingDetector.updateInfo(messageManager);
        this.actionFireFighting.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);
        this.commandExecutorFire.updateInfo(messageManager);
        this.commandExecutorScout.updateInfo(messageManager);

        FireBrigade agent = (FireBrigade) agentInfo.me();
        EntityID agentID = agentInfo.getID();
        // command
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;
            if(command.isToIDDefined() && Objects.requireNonNull(command.getToID()).getValue() == agentID.getValue()) {
                this.recentCommand = command;
                this.commandExecutorScout.setCommand(command);
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class)) {
            CommandFire command = (CommandFire) message;
            if(command.isToIDDefined() && Objects.requireNonNull(command.getToID()).getValue() == agentID.getValue()) {
                this.recentCommand = command;
                this.commandExecutorFire.setCommand(command);
            }
        }
        if(this.recentCommand != null) {
            Action action = null;
            if(this.recentCommand.getClass() == CommandFire.class) {
                action = this.commandExecutorFire.calc().getAction();
            } else if(this.recentCommand.getClass() == CommandScout.class){
                action = this.commandExecutorScout.calc().getAction();
            }
            if (action != null) {
                this.sendActionMessage(messageManager, agent, action);
                return action;
            }
        }
        // autonomous
        EntityID target = this.buildingDetector.calc().getTarget();
        Action action = this.actionFireFighting.setTarget(target).calc().getAction();
        if(action != null) {
            this.sendActionMessage(messageManager, agent, action);
            return action;
        }
        target = this.search.calc().getTarget();
        action = this.actionExtMove.setTarget(target).calc().getAction();
        if(action != null) {
            this.sendActionMessage(messageManager, agent, action);
            return action;
        }

        messageManager.addMessage(
                new MessageFireBrigade(true, agent, MessageFireBrigade.ACTION_REST,  agent.getPosition())
        );
        return new ActionRest();
    }

    private void sendActionMessage(MessageManager messageManager, FireBrigade agent, Action action) {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if (actionClass == ActionMove.class) {
            actionIndex = MessageFireBrigade.ACTION_MOVE;
            List<EntityID> path = ((ActionMove) action).getPath();
            if(path.size() > 0) {
                target = path.get(path.size() - 1);
            }
        } else if(actionClass == ActionExtinguish.class) {
            actionIndex = MessageFireBrigade.ACTION_EXTINGUISH;
            target = ((ActionExtinguish)action).getTarget();
        } else if(actionClass == ActionRefill.class) {
            actionIndex = MessageFireBrigade.ACTION_REFILL;
            target = agent.getPosition();
        } else if(actionClass == ActionRest.class) {
            actionIndex = MessageFireBrigade.ACTION_REST;
            target = agent.getPosition();
        }
        if(actionIndex != -1) {
            messageManager.addMessage(new MessageFireBrigade(true, agent, actionIndex, target));
        }
    }
}
