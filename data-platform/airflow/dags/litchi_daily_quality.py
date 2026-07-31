from datetime import datetime

from airflow import DAG
from airflow.operators.bash import BashOperator


with DAG(
    dag_id="litchi_daily_quality",
    start_date=datetime(2026, 7, 1),
    schedule="0 2 * * *",
    catchup=False,
    tags=["litchi", "quality", "analytics"],
) as dag:
    dbt_build = BashOperator(
        task_id="dbt_build_and_test",
        bash_command="cd /opt/litchi/data-platform/dbt && dbt build --profiles-dir .",
    )

    check_freshness = BashOperator(
        task_id="check_cdc_freshness",
        bash_command="python /opt/litchi/data-platform/airflow/check_cdc_freshness.py",
    )

    dbt_build >> check_freshness
