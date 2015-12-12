(defci)

(defjob test-simple-ci
  :test (execute "run-tests.sh")
  :triggers [{:type :timer
              :name "test-simple-ci-trigger"
              :args [:every 1 :minutes]}])
