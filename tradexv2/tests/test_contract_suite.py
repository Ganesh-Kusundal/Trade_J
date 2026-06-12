import pytest
from broker.simulation.sim_broker import SimulationBroker
from broker.dhan.dhan_broker import DhanBroker
from broker.testing.contract_suite import verify_broker_contract


def test_simulation_broker_contract():
    broker = SimulationBroker()
    # Contract suite check
    verify_broker_contract(broker)


def test_dhan_broker_sandbox_contract():
    # Instantiating DhanBroker with mock parameters in sandbox mode
    # It should fall back to simulated operations or handle requests gracefully
    broker = DhanBroker(client_id="mock_id", access_token="dummy", sandbox=True)
    verify_broker_contract(broker)
