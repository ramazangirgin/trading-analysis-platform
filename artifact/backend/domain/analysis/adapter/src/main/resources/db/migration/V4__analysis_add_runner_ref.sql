-- Analysis domain. The runner's reference to a started run (a process id, later a container id),
-- so a restarted platform can find the run again and follow or reconcile it.
ALTER TABLE analyses ADD COLUMN runner_ref TEXT;
